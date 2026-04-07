package com.example.bff.application;

import com.example.bff.config.BffProperties;
import com.example.bff.domain.model.ActivityEvent;
import com.example.bff.domain.model.DashboardData;
import com.example.bff.domain.model.NotificationOverview;
import com.example.bff.domain.model.UserProfile;
import com.example.bff.domain.port.ActivityServicePort;
import com.example.bff.domain.port.NotificationServicePort;
import com.example.bff.domain.port.UserServicePort;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Aggregates the dashboard data from the three downstream services in parallel.
 *
 * <p>The aggregation is intentionally <em>resilient</em>: when one of the downstream services
 * fails or times out we fall back to an empty payload for that section instead of failing the
 * entire response.
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
