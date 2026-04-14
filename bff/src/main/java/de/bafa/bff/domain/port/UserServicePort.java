package de.bafa.bff.domain.port;

import de.bafa.bff.domain.model.UserProfile;
import reactor.core.publisher.Mono;

/**
 * Port (in the hexagonal-architecture sense) for retrieving user profiles.
 *
 * <p><b>Why an interface?</b> The {@code application} layer (e.g. {@link
 * de.bafa.bff.application.DashboardAggregationService}) depends only on this port, never on the
 * concrete {@link de.bafa.bff.adapter.client.UserServiceClient}. That lets tests stub the port
 * with a pure {@code Mono.just(...)} response, and lets a future replacement (e.g. a gRPC adapter
 * or an in-process implementation) drop in without touching the application code.
 */
public interface UserServicePort {

  /**
   * Loads the profile of the given user.
   *
   * @param userId opaque identifier of the user (the OIDC subject)
   * @param accessToken bearer token used for the downstream call
   * @return the user profile
   */
  Mono<UserProfile> getUserProfile(String userId, String accessToken);
}
