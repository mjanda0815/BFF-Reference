package de.bafa.bff.application;

import de.bafa.bff.config.BffProperties;
import de.bafa.bff.domain.model.ActivityEvent;
import de.bafa.bff.domain.model.AggregationStepEntry;
import de.bafa.bff.domain.model.DashboardData;
import de.bafa.bff.domain.model.DashboardResult;
import de.bafa.bff.domain.model.NotificationOverview;
import de.bafa.bff.domain.model.UserProfile;
import de.bafa.bff.domain.port.ActivityServicePort;
import de.bafa.bff.domain.port.NotificationServicePort;
import de.bafa.bff.domain.port.UserServicePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aggregates the dashboard data from the three downstream services in parallel — the canonical
 * example of the BFF aggregation pattern.
 *
 * <p><b>Why aggregate on the server?</b> A naive SPA would fire three separate XHRs to the three
 * services and assemble the dashboard in the browser. That costs three round-trips (over possibly
 * mobile networks), requires the SPA to understand the microservice topology, and exposes three
 * authentication boundaries to the client. Moving the fan-out to the BFF collapses the network
 * cost to one request and keeps the SPA free of microservice awareness.
 *
 * <p><b>How the parallelism works:</b> each downstream call is wrapped as its own {@code Mono},
 * given an explicit {@code timeout}, subscribed on the {@code parallel} scheduler, and combined
 * via {@link Mono#zip}. Zip subscribes to all sources at once, so the wall-clock latency is the
 * slowest survivor — not the sum.
 *
 * <p><b>Partial-failure tolerance:</b> every Mono has its own {@code onErrorResume} fallback that
 * returns an empty payload for that section. A single misbehaving downstream therefore yields a
 * dashboard with one empty widget rather than a 5xx for the whole screen. The SPA is expected to
 * render those empty states as "no data" without its own error handling.
 *
 * <p><b>Execution protocol:</b> each invocation now records start/success/failure events into a
 * thread-safe {@link CopyOnWriteArrayList} and returns it alongside the aggregated data as a
 * {@link DashboardResult}. The SPA renders the log verbatim — symmetric to how
 * {@link DistributedWriteSagaOrchestrator} returns its saga log. The didactic point of the
 * protocol is to make the parallelism observable: a presenter can see three started/succeeded
 * pairs interleave in real time instead of having to claim it.
 *
 * <p><b>For teams copying this pattern:</b> keep three things consistent across all aggregated
 * calls — (1) the same timeout source ({@link BffProperties#getServiceTimeoutMillis()}), (2) the
 * same scheduler, and (3) a <em>typed</em> fallback ({@code .empty()}) rather than
 * {@code Mono.empty()} so the {@code zip} never misses a slot.
 *
 * <p><b>Für die Übernahme in ein neues Produkt:</b> Die {@code recordStarted}/
 * {@code recordSucceeded}/{@code recordFailed}-Hooks sind die Stellen, an denen pro
 * Downstream-Aufruf eine Log-Zeile entsteht. Wer einen vierten oder fünften Service einhängt,
 * wickelt ihn nach demselben Muster (Timeout + parallel-Scheduler + typed-Fallback +
 * Log-Hooks) ein und erweitert {@link Mono#zip}. Die Log-Form
 * ({@link AggregationStepEntry}) bleibt unverändert.
 */
@Service
public class DashboardAggregationService {

  private static final Logger log = LoggerFactory.getLogger(DashboardAggregationService.class);

  private static final String STEP_USER = "user-service.profile";
  private static final String STEP_NOTIFICATIONS = "notification-service.overview";
  private static final String STEP_ACTIVITY = "activity-service.recentActivity";

  private final UserServicePort userServicePort;
  private final NotificationServicePort notificationServicePort;
  private final ActivityServicePort activityServicePort;
  private final Duration timeout;

  public DashboardAggregationService(
      UserServicePort userServicePort,
      NotificationServicePort notificationServicePort,
      ActivityServicePort activityServicePort,
      BffProperties properties) {
    this.userServicePort = userServicePort;
    this.notificationServicePort = notificationServicePort;
    this.activityServicePort = activityServicePort;
    this.timeout = Duration.ofMillis(properties.getServiceTimeoutMillis());
  }

  /**
   * Builds the aggregated dashboard payload for the given user, plus the execution log of how
   * each downstream contributed.
   *
   * <p>The log is captured in a thread-safe list because the three Monos run on the parallel
   * scheduler; their {@code doOnSubscribe}/{@code doOnSuccess}/{@code doOnError} callbacks
   * append concurrently. Order in the log reflects observation order, which on a healthy run
   * is interleaved — exactly the visualisation the demo wants to make visible.
   */
  public Mono<DashboardResult> buildDashboard(String userId, String accessToken) {
    List<AggregationStepEntry> entries = new CopyOnWriteArrayList<>();
    record(entries, AggregationStepEntry.PHASE_COORDINATOR,
        "aggregation",
        AggregationStepEntry.STATUS_STARTED,
        "fan-out to three downstream services in parallel");

    Mono<UserProfile> profile =
        userServicePort
            .getUserProfile(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .doOnSubscribe(s -> recordStarted(entries, STEP_USER))
            .doOnSuccess(p -> recordSucceeded(entries, STEP_USER))
            .onErrorResume(ex -> onUserFailure(entries, ex));

    Mono<NotificationOverview> notifications =
        notificationServicePort
            .getNotifications(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .doOnSubscribe(s -> recordStarted(entries, STEP_NOTIFICATIONS))
            .doOnSuccess(n -> recordSucceeded(entries, STEP_NOTIFICATIONS))
            .onErrorResume(ex -> onNotificationFailure(entries, ex));

    Mono<List<ActivityEvent>> activity =
        activityServicePort
            .getRecentActivity(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .doOnSubscribe(s -> recordStarted(entries, STEP_ACTIVITY))
            .doOnSuccess(a -> recordSucceeded(entries, STEP_ACTIVITY))
            .onErrorResume(ex -> onActivityFailure(entries, ex));

    return Mono.zip(profile, notifications, activity)
        .map(
            tuple -> {
              record(entries, AggregationStepEntry.PHASE_COORDINATOR,
                  "aggregation",
                  AggregationStepEntry.STATUS_SUCCEEDED,
                  "all three sections combined");
              DashboardData data = new DashboardData(tuple.getT1(), tuple.getT2(), tuple.getT3());
              return new DashboardResult(data, List.copyOf(entries));
            });
  }

  private void recordStarted(List<AggregationStepEntry> entries, String step) {
    record(entries, AggregationStepEntry.PHASE_FORWARD, step,
        AggregationStepEntry.STATUS_STARTED, "calling downstream");
  }

  private void recordSucceeded(List<AggregationStepEntry> entries, String step) {
    record(entries, AggregationStepEntry.PHASE_FORWARD, step,
        AggregationStepEntry.STATUS_SUCCEEDED, "step completed");
  }

  private void recordFailed(List<AggregationStepEntry> entries, String step, Throwable ex) {
    String detail =
        "fallback to empty payload — " + (ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());
    record(entries, AggregationStepEntry.PHASE_FORWARD, step,
        AggregationStepEntry.STATUS_FAILED, detail);
  }

  private static void record(
      List<AggregationStepEntry> entries, String phase, String step, String status, String detail) {
    entries.add(new AggregationStepEntry(Instant.now(), step, phase, status, detail));
  }

  private Mono<UserProfile> onUserFailure(List<AggregationStepEntry> entries, Throwable ex) {
    log.warn("user-service unavailable, returning empty profile: {}", ex.getMessage());
    recordFailed(entries, STEP_USER, ex);
    return Mono.just(UserProfile.empty());
  }

  private Mono<NotificationOverview> onNotificationFailure(
      List<AggregationStepEntry> entries, Throwable ex) {
    log.warn("notification-service unavailable, returning empty notifications: {}", ex.getMessage());
    recordFailed(entries, STEP_NOTIFICATIONS, ex);
    return Mono.just(NotificationOverview.empty());
  }

  private Mono<List<ActivityEvent>> onActivityFailure(
      List<AggregationStepEntry> entries, Throwable ex) {
    log.warn("activity-service unavailable, returning empty activity list: {}", ex.getMessage());
    recordFailed(entries, STEP_ACTIVITY, ex);
    return Mono.just(List.of());
  }
}
