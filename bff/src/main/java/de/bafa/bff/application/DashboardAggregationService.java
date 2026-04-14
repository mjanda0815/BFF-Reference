package de.bafa.bff.application;

import de.bafa.bff.config.BffProperties;
import de.bafa.bff.domain.model.ActivityEvent;
import de.bafa.bff.domain.model.DashboardData;
import de.bafa.bff.domain.model.NotificationOverview;
import de.bafa.bff.domain.model.UserProfile;
import de.bafa.bff.domain.port.ActivityServicePort;
import de.bafa.bff.domain.port.NotificationServicePort;
import de.bafa.bff.domain.port.UserServicePort;
import java.time.Duration;
import java.util.List;
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
 * <p><b>For teams copying this pattern:</b> keep three things consistent across all aggregated
 * calls — (1) the same timeout source ({@link BffProperties#getServiceTimeoutMillis()}), (2) the
 * same scheduler, and (3) a <em>typed</em> fallback ({@code .empty()}) rather than
 * {@code Mono.empty()} so the {@code zip} never misses a slot.
 */
@Service
public class DashboardAggregationService {

  private static final Logger log = LoggerFactory.getLogger(DashboardAggregationService.class);

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

  /** Builds the aggregated dashboard payload for the given user. */
  public Mono<DashboardData> buildDashboard(String userId, String accessToken) {
    Mono<UserProfile> profile =
        userServicePort
            .getUserProfile(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .onErrorResume(this::onUserFailure);

    Mono<NotificationOverview> notifications =
        notificationServicePort
            .getNotifications(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .onErrorResume(this::onNotificationFailure);

    Mono<List<ActivityEvent>> activity =
        activityServicePort
            .getRecentActivity(userId, accessToken)
            .timeout(timeout)
            .subscribeOn(Schedulers.parallel())
            .onErrorResume(this::onActivityFailure);

    return Mono.zip(profile, notifications, activity)
        .map(tuple -> new DashboardData(tuple.getT1(), tuple.getT2(), tuple.getT3()));
  }

  private Mono<UserProfile> onUserFailure(Throwable ex) {
    log.warn("user-service unavailable, returning empty profile: {}", ex.getMessage());
    return Mono.just(UserProfile.empty());
  }

  private Mono<NotificationOverview> onNotificationFailure(Throwable ex) {
    log.warn("notification-service unavailable, returning empty notifications: {}", ex.getMessage());
    return Mono.just(NotificationOverview.empty());
  }

  private Mono<List<ActivityEvent>> onActivityFailure(Throwable ex) {
    log.warn("activity-service unavailable, returning empty activity list: {}", ex.getMessage());
    return Mono.just(List.of());
  }
}
