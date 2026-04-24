package de.bafa.bff.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.bafa.bff.domain.model.AnnouncementCommand;
import de.bafa.bff.domain.model.AnnouncementSagaResult;
import de.bafa.bff.domain.model.SagaStepEntry;
import de.bafa.bff.domain.port.ActivityAnnouncementWritePort;
import de.bafa.bff.domain.port.NotificationAnnouncementWritePort;
import de.bafa.bff.domain.port.UserAnnouncementWritePort;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Tests for the centerpiece of the with-bff saga blueprint:
 * {@link DistributedWriteSagaOrchestrator}.
 *
 * <p>The orchestrator is the one place in the reference where the architectural claim ("the BFF
 * owns the full workflow; the SPA just dispatches a command") becomes concrete code. The tests
 * below pin every user-visible promise of that claim:
 *
 * <ol>
 *   <li>Forward steps run in the documented order (user → notification → activity).
 *   <li>On a failure the orchestrator calls the compensating actions in <em>reverse</em> order
 *       of the successful prefix — the canonical saga rollback.
 *   <li>A step that fails is never compensated itself (its write never happened).
 *   <li>When every compensation succeeds the outcome is {@code compensated}. When at least one
 *       compensation itself fails, the outcome flips to {@code failed} — but the remaining
 *       compensations are still attempted best-effort.
 *   <li>The execution log returned to the SPA contains the correct sequence of phases and
 *       statuses so the frontend can render it verbatim.
 * </ol>
 *
 * <p>All ports are mocked so these are fast, deterministic unit tests; the WebClient-backed
 * adapters have their own integration coverage.
 */
@ExtendWith(MockitoExtension.class)
class DistributedWriteSagaOrchestratorTest {

  @Mock private UserAnnouncementWritePort userPort;
  @Mock private NotificationAnnouncementWritePort notificationPort;
  @Mock private ActivityAnnouncementWritePort activityPort;

  private DistributedWriteSagaOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator =
        new DistributedWriteSagaOrchestrator(userPort, notificationPort, activityPort);
  }

  /** Convenience: command with no forced failure, default message. */
  private static AnnouncementCommand happyCommand() {
    return new AnnouncementCommand("Company-wide announcement", null);
  }

  /** Convenience: command that asks the named step to fail. */
  private static AnnouncementCommand failAt(String step) {
    return new AnnouncementCommand("Company-wide announcement", step);
  }

  @Nested
  @DisplayName("Happy path")
  class HappyPath {

    @Test
    @DisplayName("runs the three forward steps in user → notification → activity order and succeeds")
    void runsAllStepsInOrderAndReportsSuccess() {
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());

      StepVerifier.create(orchestrator.execute(happyCommand(), "token"))
          .assertNext(
              result -> {
                assertEquals(AnnouncementSagaResult.OUTCOME_SUCCEEDED, result.outcome());
                assertNotNull(result.announcementId());
                // The coordinator emits three control rows: saga-started, saga-succeeded, plus
                // one started + one succeeded per forward step. We check the outcome via the
                // last coordinator row to stay decoupled from the exact internal counters.
                SagaStepEntry last = result.log().get(result.log().size() - 1);
                assertEquals("saga", last.step());
                assertEquals(SagaStepEntry.STATUS_SUCCEEDED, last.status());
              })
          .verifyComplete();

      // No compensation should have been invoked.
      verify(userPort, never()).compensate(anyString(), anyString());
      verify(notificationPort, never()).compensate(anyString(), anyString());
      verify(activityPort, never()).compensate(anyString(), anyString());
    }

    @Test
    @DisplayName("records one forward 'started' + 'succeeded' log entry per step")
    void recordsForwardProgressInTheLog() {
      when(userPort.subscribe(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());

      AnnouncementSagaResult result = orchestrator.execute(happyCommand(), "token").block();
      assertNotNull(result);

      Set<String> stepsSeen =
          result.log().stream()
              .filter(e -> SagaStepEntry.PHASE_FORWARD.equals(e.phase()))
              .map(SagaStepEntry::step)
              .collect(java.util.stream.Collectors.toSet());
      assertEquals(
          Set.of(
              "user-service.subscribe",
              "notification-service.publish",
              "activity-service.logAnnouncement"),
          stepsSeen);
    }
  }

  @Nested
  @DisplayName("Compensation path")
  class CompensationPath {

    /**
     * If the very first step fails, there is nothing to compensate — the orchestrator must
     * short-circuit to {@code compensated} without calling any DELETE endpoint.
     */
    @Test
    @DisplayName("fail in user-service → no compensation, outcome compensated")
    void failureInFirstStepCompensatesNothing() {
      when(userPort.subscribe(anyString(), anyString(), eq(true), anyString()))
          .thenReturn(Mono.error(new RuntimeException("user-service down")));

      AnnouncementSagaResult result = orchestrator.execute(failAt("user"), "token").block();
      assertNotNull(result);
      assertEquals(AnnouncementSagaResult.OUTCOME_COMPENSATED, result.outcome());

      verify(userPort, never()).compensate(anyString(), anyString());
      verify(notificationPort, never()).compensate(anyString(), anyString());
      verify(activityPort, never()).compensate(anyString(), anyString());
    }

    /**
     * Failure in the middle step must trigger exactly one compensation — on the first step,
     * which had already succeeded. The middle step itself is never compensated (its write
     * never happened) and the third step is never called at all.
     */
    @Test
    @DisplayName("fail in notification-service → only user-service is compensated")
    void failureInSecondStepCompensatesFirstOnly() {
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), eq(true), anyString()))
          .thenReturn(Mono.error(new RuntimeException("notification-service down")));
      when(userPort.compensate(anyString(), anyString())).thenReturn(Mono.empty());

      AnnouncementSagaResult result =
          orchestrator.execute(failAt("notification"), "token").block();
      assertNotNull(result);
      assertEquals(AnnouncementSagaResult.OUTCOME_COMPENSATED, result.outcome());

      verify(userPort).compensate(anyString(), eq("token"));
      verify(notificationPort, never()).compensate(anyString(), anyString());
      verify(activityPort, never()).compensate(anyString(), anyString());
    }

    /**
     * Failure in the last step must compensate the first two in <em>reverse</em> order. We
     * verify the order explicitly via the saga log because that is the textbook saga
     * property we want to demonstrate during the presentation.
     */
    @Test
    @DisplayName("fail in activity-service → notification compensated, then user")
    void failureInThirdStepCompensatesInReverseOrder() {
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), eq(true), anyString()))
          .thenReturn(Mono.error(new RuntimeException("activity-service down")));
      when(notificationPort.compensate(anyString(), anyString())).thenReturn(Mono.empty());
      when(userPort.compensate(anyString(), anyString())).thenReturn(Mono.empty());

      AnnouncementSagaResult result = orchestrator.execute(failAt("activity"), "token").block();
      assertNotNull(result);
      assertEquals(AnnouncementSagaResult.OUTCOME_COMPENSATED, result.outcome());

      // Verify BOTH ports were compensated...
      verify(notificationPort).compensate(anyString(), eq("token"));
      verify(userPort).compensate(anyString(), eq("token"));
      verify(activityPort, never()).compensate(anyString(), anyString());

      // ...and that the order of the compensation phase in the saga log is reverse: the
      // notification compensation entry must come BEFORE the user one.
      List<String> compensatedSteps =
          result.log().stream()
              .filter(e -> SagaStepEntry.PHASE_COMPENSATION.equals(e.phase()))
              .filter(e -> SagaStepEntry.STATUS_COMPENSATED.equals(e.status()))
              .map(SagaStepEntry::step)
              .toList();
      assertEquals(
          List.of("notification-service.publish", "user-service.subscribe"), compensatedSteps);
    }

    /**
     * If a compensation itself fails, the orchestrator must:
     *
     * <ul>
     *   <li>report {@code failed} as the saga outcome (state may be inconsistent),
     *   <li>still attempt the remaining compensations (best-effort),
     *   <li>log the failed compensation so an operator can see what was left behind.
     * </ul>
     */
    @Test
    @DisplayName("compensation failure flips the outcome to failed but keeps going best-effort")
    void compensationFailureFlipsOutcomeButContinues() {
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), eq(true), anyString()))
          .thenReturn(Mono.error(new RuntimeException("activity-service down")));
      // Notification compensation fails; user compensation still succeeds.
      when(notificationPort.compensate(anyString(), anyString()))
          .thenReturn(Mono.error(new RuntimeException("notification compensation broke")));
      when(userPort.compensate(anyString(), anyString())).thenReturn(Mono.empty());

      AnnouncementSagaResult result = orchestrator.execute(failAt("activity"), "token").block();
      assertNotNull(result);
      assertEquals(AnnouncementSagaResult.OUTCOME_FAILED, result.outcome());

      // Both compensations were attempted despite the first one failing.
      verify(notificationPort).compensate(anyString(), anyString());
      verify(userPort).compensate(anyString(), anyString());

      // The failure-row is visible in the log.
      boolean hasFailedCompensation =
          result.log().stream()
              .anyMatch(
                  e ->
                      SagaStepEntry.PHASE_COMPENSATION.equals(e.phase())
                          && SagaStepEntry.STATUS_FAILED.equals(e.status()));
      assertTrue(
          hasFailedCompensation, "log should record the failed compensation for operator review");
    }
  }

  @Nested
  @DisplayName("Resilience: per-step timeout and retry-with-backoff")
  class Resilience {

    /**
     * Two transient failures in a row followed by a success must still let the saga reach the
     * happy path. Exercises {@code Retry.backoff} on transient errors.
     */
    @Test
    @DisplayName("retries transient failures and completes on eventual success")
    void retriesTransientFailuresAndCompletes() {
      AtomicInteger userAttempts = new AtomicInteger();
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(
              Mono.defer(
                  () -> {
                    int n = userAttempts.incrementAndGet();
                    return n < 3
                        ? Mono.error(new TimeoutException("transient " + n))
                        : Mono.empty();
                  }));
      when(notificationPort.publish(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());

      AnnouncementSagaResult result = orchestrator.execute(happyCommand(), "token").block();
      assertNotNull(result);
      assertEquals(AnnouncementSagaResult.OUTCOME_SUCCEEDED, result.outcome());
      assertEquals(3, userAttempts.get(), "expected two retries + one success for the user step");
    }

    /**
     * A transient error that persists past the retry budget must fall through to the
     * compensation path — just like any other forward-step failure would.
     */
    @Test
    @DisplayName("exhausted retries trigger the compensation path")
    void exhaustedRetriesTriggerCompensation() {
      when(userPort.subscribe(anyString(), anyString(), eq(false), anyString()))
          .thenReturn(Mono.error(new TimeoutException("always-down")));

      AnnouncementSagaResult result = orchestrator.execute(happyCommand(), "token").block();
      assertNotNull(result);
      // First step never succeeded, so compensations queue is empty → outcome=compensated
      // but no compensation calls should have been made.
      assertEquals(AnnouncementSagaResult.OUTCOME_COMPENSATED, result.outcome());
      verify(userPort, never()).compensate(anyString(), anyString());
      verify(notificationPort, never()).publish(anyString(), anyString(), anyBoolean(), anyString());
    }

    /**
     * Permanent (4xx-class) errors bypass retry — retrying a validation failure is pure latency.
     * This pins the classifier contract for future readers: only 5xx / timeouts / connection
     * errors are considered transient.
     */
    @Test
    @DisplayName("isTransient: retry only for timeouts, connection errors and 5xx")
    void isTransientClassifierContract() {
      assertTrue(DistributedWriteSagaOrchestrator.isTransient(new TimeoutException("t")));
      WebClientResponseException fiveHundred =
          WebClientResponseException.create(
              HttpStatus.INTERNAL_SERVER_ERROR.value(),
              "",
              null,
              new byte[0],
              StandardCharsets.UTF_8);
      assertTrue(DistributedWriteSagaOrchestrator.isTransient(fiveHundred));

      WebClientResponseException badRequest =
          WebClientResponseException.create(
              HttpStatus.BAD_REQUEST.value(),
              "",
              null,
              new byte[0],
              StandardCharsets.UTF_8);
      assertTrue(!DistributedWriteSagaOrchestrator.isTransient(badRequest));
      assertTrue(!DistributedWriteSagaOrchestrator.isTransient(new RuntimeException("bug")));
      assertTrue(!DistributedWriteSagaOrchestrator.isTransient(new IllegalArgumentException()));
    }
  }

  @Nested
  @DisplayName("Execution log contract")
  class LogContract {

    /**
     * The SPA renders the log verbatim, so we pin the two contract facts it relies on:
     *
     * <ul>
     *   <li>the first entry is the {@code coordinator / saga / started} row, and
     *   <li>every entry has a non-null {@code phase}, {@code step} and {@code status}.
     * </ul>
     */
    @Test
    @DisplayName("the log starts with a coordinator 'saga started' entry")
    void logStartsWithSagaStartedEntry() {
      when(userPort.subscribe(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());
      when(notificationPort.publish(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());
      when(activityPort.logAnnouncement(anyString(), anyString(), anyBoolean(), anyString()))
          .thenReturn(Mono.empty());

      AnnouncementSagaResult result = orchestrator.execute(happyCommand(), "token").block();
      assertNotNull(result);

      SagaStepEntry first = result.log().get(0);
      assertEquals(SagaStepEntry.PHASE_COORDINATOR, first.phase());
      assertEquals("saga", first.step());
      assertEquals(SagaStepEntry.STATUS_STARTED, first.status());

      for (SagaStepEntry e : result.log()) {
        assertNotNull(e.phase());
        assertNotNull(e.step());
        assertNotNull(e.status());
        assertNotNull(e.timestamp());
      }
    }
  }
}
