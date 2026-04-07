package io.janda.bff.application;

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
 * Manages the OAuth2 token lifecycle on behalf of a session: lookup, refresh, removal.
 *
 * <p>The frontend never sees these tokens — they live exclusively server-side, persisted in the
 * Redis-backed Spring Session.
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
