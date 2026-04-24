package de.bafa.bff.application;

import de.bafa.bff.domain.model.AnnouncementCommand;
import de.bafa.bff.domain.model.AnnouncementSagaResult;
import de.bafa.bff.domain.model.SagaStepEntry;
import de.bafa.bff.domain.port.ActivityAnnouncementWritePort;
import de.bafa.bff.domain.port.NotificationAnnouncementWritePort;
import de.bafa.bff.domain.port.UserAnnouncementWritePort;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Backend orchestrator of the distributed-write saga.
 *
 * <p>This class encapsulates <em>all</em> of the complexity of running a multi-service write on
 * behalf of the SPA: step sequencing, workflow state, per-step failure detection, reverse-order
 * compensation and execution logging. The SPA's responsibility collapses to "dispatch one
 * command, render the returned log".
 *
 * <p>The architectural point — and the reason this class exists in the reference — is the
 * comparison with a frontend-hosted orchestrator: keeping this logic here means the SPA never
 * sees tokens, never knows the service topology, never owns partial-failure state. See the
 * accompanying presentation ({@code docs/BFF_Demo_Praesentation.pptx}, slide 7) and
 * {@code docs/adr/ADR-006-write-saga.md}.
 *
 * <p>Explicitly scoped to a per-invocation mutable state object ({@link SagaExecution}) so a
 * concurrent request never shares a log list or compensation stack with another.
 *
 * <h2>Resilience knobs</h2>
 *
 * <p>Each forward step is wrapped with two production-flavoured safety nets:
 *
 * <ul>
 *   <li><b>Per-step timeout</b> ({@link #FORWARD_STEP_TIMEOUT}). A downstream that hangs gets
 *       cancelled with a {@link TimeoutException}, which is treated as a transient failure and
 *       retried. Caps the worst-case time a single forward step can block the saga.
 *   <li><b>Retry with exponential backoff</b> on <em>transient</em> errors only
 *       ({@link #FORWARD_RETRY_ATTEMPTS} attempts, initial delay
 *       {@link #FORWARD_RETRY_INITIAL_BACKOFF}). Transient here means connection reset,
 *       5xx responses or a step-level timeout — things a retry could plausibly fix.
 *       Client errors (4xx, including validation failures) bypass retry. Relies on the
 *       downstream stores being idempotent on the shared {@code announcementId}.
 * </ul>
 *
 * <p>Compensations use the same per-step timeout but <b>no retry</b>: compensation is
 * intentionally best-effort; retry loops there would lengthen the request and rarely help —
 * the partial-failure outcome is the signal operators act on.
 *
 * <h2>Für die Übernahme in ein neues Produkt</h2>
 *
 * <p>Dieser Orchestrator ist das Muster, wenn ein neues Produkt einen eigenen verteilten
 * Schreibvorgang braucht. Typische Anpassungen:
 *
 * <ul>
 *   <li><b>Fachliche Ports ersetzen</b>. Statt der drei Announcement-Ports die eigenen
 *       Schritt-Ports einziehen (z. B. {@code OrderValidationPort},
 *       {@code InventoryReservationPort}, …). Die Ports bleiben klein und klar
 *       Forward-/Compensate-strukturiert.
 *   <li><b>Step-Reihenfolge festlegen</b>. Die drei {@code runForwardStep(...)}-Aufrufe
 *       einfach durch die Produkt-Steps ersetzen; Reihenfolge = Abhängigkeitsreihenfolge.
 *   <li><b>Resilienz-Konstanten justieren</b>. {@link #FORWARD_STEP_TIMEOUT} +
 *       {@link #FORWARD_RETRY_ATTEMPTS} + {@link #FORWARD_RETRY_INITIAL_BACKOFF} sind bewusst
 *       package-private und konstant. Wer produktspezifisch tunen will, macht daraus
 *       {@code @ConfigurationProperties}-Werte und dokumentiert die neuen Properties im
 *       Abschnitt {@code 9} der Confluence-Doku.
 *   <li><b>Store-Idempotenz auf Service-Seite wahren</b>. Der Retry ist nur dann sicher,
 *       wenn die Services den gleichen Write bei gleichem Key nicht doppelt ausführen. Die
 *       {@code Announcement*Store}-Klassen zeigen das mit {@code putIfAbsent}. Bei einem
 *       Wechsel auf JPA: {@code @Id}-Konflikte in Upserts korrekt behandeln.
 * </ul>
 *
 * <p>Nicht anfassen, wenn möglich: die Kompensations-Schleife in
 * {@link #compensate(SagaExecution, String, String, Throwable)}. Sie implementiert den
 * Best-Effort-Vertrag „alle Kompensationen versuchen, aber Teilfehler im Ergebnis sichtbar
 * machen", der in ADR-006 als tragende Eigenschaft festgehalten ist.
 */
@Service
public class DistributedWriteSagaOrchestrator {

  private static final Logger log =
      LoggerFactory.getLogger(DistributedWriteSagaOrchestrator.class);

  private static final String STEP_USER = "user-service.subscribe";
  private static final String STEP_NOTIFICATION = "notification-service.publish";
  private static final String STEP_ACTIVITY = "activity-service.logAnnouncement";

  /** Per-forward-step timeout. A hanging downstream is cancelled + retried. */
  static final Duration FORWARD_STEP_TIMEOUT = Duration.ofSeconds(5);

  /** Per-compensation-step timeout. Compensation is best-effort; no retry. */
  static final Duration COMPENSATION_STEP_TIMEOUT = Duration.ofSeconds(5);

  /** Retry attempts after the initial call (so total calls = attempts + 1). */
  static final long FORWARD_RETRY_ATTEMPTS = 2;

  /** Initial backoff before the first retry; doubles on each subsequent attempt. */
  static final Duration FORWARD_RETRY_INITIAL_BACKOFF = Duration.ofMillis(200);

  private final UserAnnouncementWritePort userPort;
  private final NotificationAnnouncementWritePort notificationPort;
  private final ActivityAnnouncementWritePort activityPort;

  public DistributedWriteSagaOrchestrator(
      UserAnnouncementWritePort userPort,
      NotificationAnnouncementWritePort notificationPort,
      ActivityAnnouncementWritePort activityPort) {
    this.userPort = userPort;
    this.notificationPort = notificationPort;
    this.activityPort = activityPort;
  }

  /**
   * Runs the saga end-to-end and returns a result that is safe to render in the SPA as-is.
   *
   * <p>Forward order: user → notification → activity. Compensation order: reverse of the
   * successful prefix. A failure during compensation flips the outcome from
   * {@link AnnouncementSagaResult#OUTCOME_COMPENSATED compensated} to
   * {@link AnnouncementSagaResult#OUTCOME_FAILED failed}, but the saga still attempts every
   * remaining compensation so the operator sees exactly which downstream call was left behind.
   */
  public Mono<AnnouncementSagaResult> execute(AnnouncementCommand command, String accessToken) {
    SagaExecution execution = new SagaExecution(UUID.randomUUID().toString());
    String message = command.resolvedMessage();
    log.info(
        "saga {}: starting distributed write, failAt={}", execution.announcementId, command.failAt());
    execution.record(
        SagaStepEntry.PHASE_COORDINATOR,
        "saga",
        SagaStepEntry.STATUS_STARTED,
        "orchestrated by BFF");

    return runForwardStep(
            execution,
            STEP_USER,
            () ->
                userPort.subscribe(
                    execution.announcementId, message, command.shouldFail("user"), accessToken),
            () -> userPort.compensate(execution.announcementId, accessToken))
        .flatMap(
            ok ->
                runForwardStep(
                    execution,
                    STEP_NOTIFICATION,
                    () ->
                        notificationPort.publish(
                            execution.announcementId,
                            message,
                            command.shouldFail("notification"),
                            accessToken),
                    () -> notificationPort.compensate(execution.announcementId, accessToken)))
        .flatMap(
            ok ->
                runForwardStep(
                    execution,
                    STEP_ACTIVITY,
                    () ->
                        activityPort.logAnnouncement(
                            execution.announcementId,
                            message,
                            command.shouldFail("activity"),
                            accessToken),
                    () -> activityPort.compensate(execution.announcementId, accessToken)))
        .then(Mono.fromSupplier(() -> succeed(execution, message)))
        .onErrorResume(
            ForwardStepFailedException.class,
            ex -> compensate(execution, message, ex.getFailedStep(), ex.getCause()));
  }

  private Mono<Boolean> runForwardStep(
      SagaExecution execution,
      String step,
      java.util.function.Supplier<Mono<Void>> forward,
      java.util.function.Supplier<Mono<Void>> compensation) {
    execution.record(
        SagaStepEntry.PHASE_FORWARD, step, SagaStepEntry.STATUS_STARTED, "calling downstream");
    return forward
        .get()
        .timeout(FORWARD_STEP_TIMEOUT)
        .retryWhen(
            Retry.backoff(FORWARD_RETRY_ATTEMPTS, FORWARD_RETRY_INITIAL_BACKOFF)
                .filter(DistributedWriteSagaOrchestrator::isTransient)
                .doBeforeRetry(
                    signal ->
                        log.info(
                            "saga {}: retrying {} after transient error (attempt {}): {}",
                            execution.announcementId,
                            step,
                            signal.totalRetries() + 1,
                            rootMessage(signal.failure()))))
        .then(
            Mono.fromRunnable(
                () -> {
                  execution.record(
                      SagaStepEntry.PHASE_FORWARD,
                      step,
                      SagaStepEntry.STATUS_SUCCEEDED,
                      "step completed");
                  execution.compensations.push(new Compensation(step, compensation));
                }))
        .thenReturn(Boolean.TRUE)
        .onErrorResume(
            ex -> {
              execution.record(
                  SagaStepEntry.PHASE_FORWARD,
                  step,
                  SagaStepEntry.STATUS_FAILED,
                  rootMessage(ex));
              return Mono.error(new ForwardStepFailedException(step, ex));
            });
  }

  private Mono<AnnouncementSagaResult> compensate(
      SagaExecution execution, String message, String failedStep, Throwable cause) {
    log.info(
        "saga {}: forward step {} failed — starting compensation",
        execution.announcementId,
        failedStep);
    execution.record(
        SagaStepEntry.PHASE_COORDINATOR,
        "saga",
        SagaStepEntry.STATUS_STARTED,
        "compensating in reverse order after " + failedStep);

    // Chain compensations sequentially. AND the per-step flags so a single failure is
    // carried through to the end, even if every later compensation succeeds — that is what
    // the "best-effort but record the partial failure" contract requires.
    Mono<Boolean> chain = Mono.just(Boolean.TRUE);
    while (!execution.compensations.isEmpty()) {
      Compensation next = execution.compensations.pop();
      chain =
          chain.flatMap(
              previousOk ->
                  runCompensation(execution, next).map(thisOk -> previousOk && thisOk));
    }
    return chain.map(allOk -> finish(execution, message, allOk));
  }

  private Mono<Boolean> runCompensation(SagaExecution execution, Compensation compensation) {
    execution.record(
        SagaStepEntry.PHASE_COMPENSATION,
        compensation.step,
        SagaStepEntry.STATUS_STARTED,
        "undoing previously successful step");
    return compensation
        .action
        .get()
        .timeout(COMPENSATION_STEP_TIMEOUT)
        .then(
            Mono.fromRunnable(
                () ->
                    execution.record(
                        SagaStepEntry.PHASE_COMPENSATION,
                        compensation.step,
                        SagaStepEntry.STATUS_COMPENSATED,
                        "compensation succeeded")))
        .thenReturn(Boolean.TRUE)
        .onErrorResume(
            ex -> {
              execution.record(
                  SagaStepEntry.PHASE_COMPENSATION,
                  compensation.step,
                  SagaStepEntry.STATUS_FAILED,
                  rootMessage(ex));
              return Mono.just(Boolean.FALSE);
            });
  }

  private AnnouncementSagaResult succeed(SagaExecution execution, String message) {
    execution.record(
        SagaStepEntry.PHASE_COORDINATOR,
        "saga",
        SagaStepEntry.STATUS_SUCCEEDED,
        "all three forward steps succeeded");
    return new AnnouncementSagaResult(
        execution.announcementId,
        AnnouncementSagaResult.OUTCOME_SUCCEEDED,
        message,
        List.copyOf(execution.log));
  }

  private AnnouncementSagaResult finish(
      SagaExecution execution, String message, boolean allCompensationsOk) {
    String outcome =
        allCompensationsOk
            ? AnnouncementSagaResult.OUTCOME_COMPENSATED
            : AnnouncementSagaResult.OUTCOME_FAILED;
    execution.record(
        SagaStepEntry.PHASE_COORDINATOR,
        "saga",
        outcome,
        allCompensationsOk
            ? "all successful steps compensated"
            : "at least one compensation failed — state may be inconsistent");
    return new AnnouncementSagaResult(
        execution.announcementId, outcome, message, List.copyOf(execution.log));
  }

  private static String rootMessage(Throwable ex) {
    Throwable current = ex;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    String msg = current.getMessage();
    return msg == null ? current.getClass().getSimpleName() : msg;
  }

  /**
   * Classifies an error as transient (retry-worthy) or permanent.
   *
   * <p>Transient = a retry could plausibly fix it: connection reset, downstream 5xx response, or
   * the step hit its own timeout. 4xx responses and anything else (programming errors, validation
   * failures) are permanent — retrying them just burns latency.
   *
   * <p>Visibility is package-private so the orchestrator's unit test can pin the classification
   * without reflection.
   */
  static boolean isTransient(Throwable t) {
    if (t instanceof TimeoutException) {
      return true;
    }
    if (t instanceof WebClientRequestException) {
      return true; // connection reset, DNS failure, …
    }
    if (t instanceof WebClientResponseException response) {
      return response.getStatusCode().is5xxServerError();
    }
    return false;
  }

  /** Per-invocation mutable state. Never shared across concurrent saga runs. */
  private static final class SagaExecution {
    final String announcementId;
    final List<SagaStepEntry> log = new CopyOnWriteArrayList<>();
    final Deque<Compensation> compensations = new ArrayDeque<>();

    SagaExecution(String announcementId) {
      this.announcementId = announcementId;
    }

    void record(String phase, String step, String status, String detail) {
      log.add(new SagaStepEntry(Instant.now(), step, phase, status, detail));
    }
  }

  private record Compensation(String step, java.util.function.Supplier<Mono<Void>> action) {}

  /**
   * Internal error signal that a forward step has failed, carrying the step name so the
   * compensation phase can reason about what still needs undoing.
   */
  private static final class ForwardStepFailedException extends RuntimeException {
    private final String failedStep;

    ForwardStepFailedException(String failedStep, Throwable cause) {
      super(cause);
      this.failedStep = failedStep;
    }

    String getFailedStep() {
      return failedStep;
    }
  }
}
