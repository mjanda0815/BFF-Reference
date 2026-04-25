package de.bafa.bff.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import de.bafa.bff.config.BffProperties;
import de.bafa.bff.domain.model.ActivityEvent;
import de.bafa.bff.domain.model.AggregationStepEntry;
import de.bafa.bff.domain.model.Notification;
import de.bafa.bff.domain.model.NotificationOverview;
import de.bafa.bff.domain.model.UserProfile;
import de.bafa.bff.domain.port.ActivityServicePort;
import de.bafa.bff.domain.port.NotificationServicePort;
import de.bafa.bff.domain.port.UserServicePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DashboardAggregationServiceTest {

  @Mock private UserServicePort userServicePort;
  @Mock private NotificationServicePort notificationServicePort;
  @Mock private ActivityServicePort activityServicePort;

  private DashboardAggregationService service;

  @BeforeEach
  void setUp() {
    BffProperties properties = new BffProperties();
    properties.setServiceTimeoutMillis(2000);
    properties.setUserServiceUrl("http://x");
    properties.setNotificationServiceUrl("http://x");
    properties.setActivityServiceUrl("http://x");
    service =
        new DashboardAggregationService(
            userServicePort, notificationServicePort, activityServicePort, properties);
  }

  @Test
  void aggregatesAllThreeServicesInParallel() {
    UserProfile profile = new UserProfile("u1", "Alice", "user", "https://avatar/u1");
    NotificationOverview notifications =
        new NotificationOverview(
            1, List.of(new Notification("n1", "hi", "msg", Instant.parse("2024-01-01T00:00:00Z"))));
    List<ActivityEvent> activity =
        List.of(new ActivityEvent("a1", "LOGIN", "session", Instant.parse("2024-01-01T00:00:00Z")));

    when(userServicePort.getUserProfile(anyString(), anyString())).thenReturn(Mono.just(profile));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(notifications));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(activity));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(
            result -> {
              assertEquals(profile, result.data().profile());
              assertEquals(notifications, result.data().notifications());
              assertEquals(activity, result.data().activity());
              assertFalse(result.log().isEmpty(), "execution log must not be empty");
            })
        .verifyComplete();
  }

  /**
   * The execution-log contract the SPA renders verbatim: the log starts with a coordinator
   * "started" entry, ends with a coordinator "succeeded" entry, and contains exactly one
   * forward "started" + "succeeded" pair per downstream service. This is what makes the
   * read-side parallelism observable to a presenter.
   */
  @Test
  void executionLogContainsOnePairPerDownstreamPlusCoordinator() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.just(UserProfile.empty()));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(NotificationOverview.empty()));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(List.of()));

    var result = service.buildDashboard("u1", "token").block();
    org.junit.jupiter.api.Assertions.assertNotNull(result);

    var log = result.log();
    assertEquals(
        AggregationStepEntry.STATUS_STARTED, log.get(0).status(), "first entry: coordinator started");
    assertEquals(AggregationStepEntry.PHASE_COORDINATOR, log.get(0).phase());

    assertEquals(
        AggregationStepEntry.STATUS_SUCCEEDED,
        log.get(log.size() - 1).status(),
        "last entry: coordinator succeeded");

    Set<String> forwardSteps =
        log.stream()
            .filter(e -> AggregationStepEntry.PHASE_FORWARD.equals(e.phase()))
            .filter(e -> AggregationStepEntry.STATUS_SUCCEEDED.equals(e.status()))
            .map(AggregationStepEntry::step)
            .collect(Collectors.toSet());
    assertEquals(
        Set.of(
            "user-service.profile",
            "notification-service.overview",
            "activity-service.recentActivity"),
        forwardSteps);
  }

  @Test
  void returnsEmptyUserProfileOnUserServiceFailureAndRecordsFailedStep() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("boom")));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(NotificationOverview.empty()));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(List.of()));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(
            result -> {
              assertEquals("", result.data().profile().userId());
              boolean hasFailedUserStep =
                  result.log().stream()
                      .anyMatch(
                          e ->
                              "user-service.profile".equals(e.step())
                                  && AggregationStepEntry.STATUS_FAILED.equals(e.status()));
              assertTrue(hasFailedUserStep, "log must record the failed user-service step");
            })
        .verifyComplete();
  }

  @Test
  void returnsEmptyNotificationsOnNotificationServiceFailure() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.just(UserProfile.empty()));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("boom")));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(List.of()));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(result -> assertEquals(0, result.data().notifications().unreadCount()))
        .verifyComplete();
  }

  @Test
  void returnsEmptyActivityOnActivityServiceFailure() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.just(UserProfile.empty()));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(NotificationOverview.empty()));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("boom")));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(result -> assertTrue(result.data().activity().isEmpty()))
        .verifyComplete();
  }

  @Test
  void timeoutFallsBackToEmpty() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.just(UserProfile.empty()).delayElement(Duration.ofSeconds(5)));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(NotificationOverview.empty()));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(List.of()));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(result -> assertEquals("", result.data().profile().userId()))
        .verifyComplete();
  }
}
