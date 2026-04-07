package com.example.bff.domain.port;

import com.example.bff.domain.model.UserProfile;
import reactor.core.publisher.Mono;

/** Port for retrieving user profiles from a downstream user service. */
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
