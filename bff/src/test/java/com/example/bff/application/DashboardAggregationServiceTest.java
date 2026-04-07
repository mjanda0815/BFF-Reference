package com.example.bff.application;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.example.bff.config.BffProperties;
import com.example.bff.domain.model.ActivityEvent;
import com.example.bff.domain.model.Notification;
import com.example.bff.domain.model.NotificationOverview;
import com.example.bff.domain.model.UserProfile;
import com.example.bff.domain.port.ActivityServicePort;
import com.example.bff.domain.port.NotificationServicePort;
import com.example.bff.domain.port.UserServicePort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
            data -> {
              org.junit.jupiter.api.Assertions.assertEquals(profile, data.profile());
              org.junit.jupiter.api.Assertions.assertEquals(notifications, data.notifications());
              org.junit.jupiter.api.Assertions.assertEquals(activity, data.activity());
            })
        .verifyComplete();
  }

  @Test
  void returnsEmptyUserProfileOnUserServiceFailure() {
    when(userServicePort.getUserProfile(anyString(), anyString()))
        .thenReturn(Mono.error(new RuntimeException("boom")));
    when(notificationServicePort.getNotifications(anyString(), anyString()))
        .thenReturn(Mono.just(NotificationOverview.empty()));
    when(activityServicePort.getRecentActivity(anyString(), anyString()))
        .thenReturn(Mono.just(List.of()));

    StepVerifier.create(service.buildDashboard("u1", "token"))
        .assertNext(data -> org.junit.jupiter.api.Assertions.assertEquals("", data.profile().userId()))
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
        .assertNext(data -> org.junit.jupiter.api.Assertions.assertEquals(0, data.notifications().unreadCount()))
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
        .assertNext(data -> org.junit.jupiter.api.Assertions.assertTrue(data.activity().isEmpty()))
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
        .assertNext(data -> org.junit.jupiter.api.Assertions.assertEquals("", data.profile().userId()))
        .verifyComplete();
  }
}
