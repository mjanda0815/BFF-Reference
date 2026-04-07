package com.example.bff.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Logout handler that removes the OAuth2 authorized client (and thus the persisted refresh token)
 * for the current session.
 */
@Component
public class SessionInvalidationHandler implements ServerLogoutHandler {

  private static final Logger log = LoggerFactory.getLogger(SessionInvalidationHandler.class);
  private static final String CLIENT_REGISTRATION_ID = "keycloak";

  private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

  public SessionInvalidationHandler(ReactiveOAuth2AuthorizedClientService authorizedClientService) {
    this.authorizedClientService = authorizedClientService;
  }

  @Override
  public Mono<Void> logout(WebFilterExchange exchange, Authentication authentication) {
    if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
      return Mono.empty();
    }
    log.info("Invalidating session for principal");
    return authorizedClientService
        .removeAuthorizedClient(CLIENT_REGISTRATION_ID, oidcUser.getName())
        .onErrorResume(
            ex -> {
              log.warn("Failed to remove authorized client: {}", ex.getMessage());
              return Mono.empty();
            });
  }
}
