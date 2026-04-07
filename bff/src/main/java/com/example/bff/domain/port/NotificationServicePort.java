package com.example.bff.domain.port;

import com.example.bff.domain.model.NotificationOverview;
import reactor.core.publisher.Mono;

/** Port for retrieving notifications from a downstream notification service. */
public interface NotificationServicePort {

  Mono<NotificationOverview> getNotifications(String userId, String accessToken);
}
