package de.bafa.bff.security;

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
 * Logout handler that removes the OAuth2 authorized client — and therefore the persisted refresh
 * token — from the session store when a user logs out.
 *
 * <p><b>Why this is a separate handler:</b> Spring Security's default logout chain invalidates
 * the web session but does <em>not</em> automatically drop the {@code OAuth2AuthorizedClient}
 * from its repository. Without this handler the refresh token would linger in Redis until the
 * session TTL expires, which is unacceptable from a security standpoint — logout must be
 * immediate. This handler is wired into the logout chain in {@link
 * de.bafa.bff.config.SecurityConfig#combinedLogoutHandler}.
 *
 * <p><b>Error tolerance:</b> if Redis is momentarily unavailable during logout we log and swallow
 * the exception. The session cookie is already being invalidated on the response, so the user is
 * logged out of the BFF regardless; the stale authorized-client entry will be cleaned up by the
 * Redis TTL at the latest.
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
