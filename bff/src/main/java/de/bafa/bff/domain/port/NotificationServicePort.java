package de.bafa.bff.domain.port;

import de.bafa.bff.domain.model.NotificationOverview;
import reactor.core.publisher.Mono;

/** Port for retrieving notifications from a downstream notification service. */
public interface NotificationServicePort {

  Mono<NotificationOverview> getNotifications(String userId, String accessToken);
}
