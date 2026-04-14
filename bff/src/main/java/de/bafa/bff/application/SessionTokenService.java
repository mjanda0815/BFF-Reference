package de.bafa.bff.application;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.ReactiveOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Manages the OAuth2 token lifecycle on behalf of a session — the single place where access and
 * refresh tokens live on the server.
 *
 * <p><b>Why this service exists:</b> in the BFF pattern the SPA never touches tokens. When the
 * dashboard controller needs an access token to call a downstream service, it asks this service,
 * which consults Spring Security's {@link
 * org.springframework.security.oauth2.client.web.server.ServerOAuth2AuthorizedClientRepository}.
 * The repository transparently refreshes an expired access token using the refresh token, so the
 * caller always gets a valid bearer value.
 *
 * <p><b>Storage:</b> the underlying {@code OAuth2AuthorizedClient} is serialised into the Spring
 * Session, which is persisted in Redis (see {@link de.bafa.bff.config.RedisConfig}). Tokens
 * therefore survive a JVM restart but are gone the moment the session expires or is invalidated.
 *
 * <p><b>Invariant:</b> every authenticated API request that reaches a controller must be able to
 * resolve an access token via {@link #currentAccessToken}. If this returns empty, the session is
 * corrupt and the controller should respond with 401 so the SPA can re-login.
 */
@Service
public class SessionTokenService {

  private static final String CLIENT_REGISTRATION_ID = "keycloak";

  private final ServerOAuth2AuthorizedClientRepository authorizedClientRepository;
  private final ReactiveOAuth2AuthorizedClientService authorizedClientService;

  public SessionTokenService(
      ServerOAuth2AuthorizedClientRepository authorizedClientRepository,
      ReactiveOAuth2AuthorizedClientService authorizedClientService) {
    this.authorizedClientRepository = authorizedClientRepository;
    this.authorizedClientService = authorizedClientService;
  }

  /** Loads the authorized client for the current authenticated session, if any. */
  public Mono<OAuth2AuthorizedClient> loadAuthorizedClient(
      OidcUser user, ServerWebExchange exchange) {
    return authorizedClientRepository.loadAuthorizedClient(
        CLIENT_REGISTRATION_ID, oidcAuthentication(user), exchange);
  }

  /** Returns the access token associated with the current session, refreshing it if needed. */
  public Mono<String> currentAccessToken(OidcUser user, ServerWebExchange exchange) {
    return loadAuthorizedClient(user, exchange)
        .map(OAuth2AuthorizedClient::getAccessToken)
        .map(OAuth2AccessToken::getTokenValue);
  }

  /** Removes the authorized client (and any persisted tokens) for the current session. */
  public Mono<Void> invalidate(OidcUser user, ServerWebExchange exchange) {
    return authorizedClientService
        .removeAuthorizedClient(CLIENT_REGISTRATION_ID, user.getName())
        .then(
            authorizedClientRepository.removeAuthorizedClient(
                CLIENT_REGISTRATION_ID, oidcAuthentication(user), exchange));
  }

  private static org.springframework.security.core.Authentication oidcAuthentication(OidcUser user) {
    return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
        user, "n/a", user.getAuthorities());
  }
}
