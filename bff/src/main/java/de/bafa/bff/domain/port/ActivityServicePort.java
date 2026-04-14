package de.bafa.bff.domain.port;

import de.bafa.bff.domain.model.ActivityEvent;
import java.util.List;
import reactor.core.publisher.Mono;

/**
 * Hexagonal port for retrieving recent activity events. See {@link UserServicePort} for the
 * rationale of the port/adapter split.
 */
public interface ActivityServicePort {

  /**
   * @param userId OIDC subject of the authenticated user
   * @param accessToken bearer token forwarded to the downstream activity-service
   */
  Mono<List<ActivityEvent>> getRecentActivity(String userId, String accessToken);
}
