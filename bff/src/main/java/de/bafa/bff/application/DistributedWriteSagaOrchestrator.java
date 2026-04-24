package de.bafa.bff.application;

import de.bafa.bff.domain.model.AnnouncementCommand;
import de.bafa.bff.domain.model.AnnouncementSagaResult;
import de.bafa.bff.domain.model.SagaStepEntry;
import de.bafa.bff.domain.port.ActivityAnnouncementWritePort;
import de.bafa.bff.domain.port.NotificationAnnouncementWritePort;
import de.bafa.bff.domain.port.UserAnnouncementWritePort;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

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
 */
@Service
public class DistributedWriteSagaOrchestrator {

  private static final Logger log =
      LoggerFactory.getLogger(DistributedWriteSagaOrchestrator.class);

  private static final String STEP_USER = "user-service.subscribe";
  private static final String STEP_NOTIFICATION = "notification-service.publish";
  private static final String STEP_ACTIVITY = "activity-service.logAnnouncement";

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
