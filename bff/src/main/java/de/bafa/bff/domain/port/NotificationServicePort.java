package de.bafa.bff.domain.port;

import de.bafa.bff.domain.model.NotificationOverview;
import reactor.core.publisher.Mono;

/**
 * Hexagonal port for retrieving notifications. See {@link UserServicePort} for the rationale of
 * the port/adapter split.
 */
public interface NotificationServicePort {

  /**
   * @param userId OIDC subject of the authenticated user
   * @param accessToken bearer token forwarded to the downstream notification-service
   */
  Mono<NotificationOverview> getNotifications(String userId, String accessToken);
}
