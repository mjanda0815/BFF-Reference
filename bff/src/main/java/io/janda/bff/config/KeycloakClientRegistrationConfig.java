package io.janda.bff.config;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

/**
 * Builds the Keycloak {@link ClientRegistration} programmatically instead of via OIDC discovery.
 *
 * <p>The BFF talks to Keycloak from two different network positions:
 *
 * <ul>
 *   <li><b>Backchannel</b> (server-to-server): token exchange, JWKS lookup, userinfo. The BFF
 *       resolves {@code keycloak:8080} via the internal docker network.
 *   <li><b>Frontchannel</b> (browser redirects): the authorization endpoint must be reachable
 *       from the user's browser — so it must use the public URL (e.g. {@code
 *       http://localhost:8080}).
 * </ul>
 *
 * <p>Auto-discovery cannot serve both at once, because the discovery document either contains the
 * internal or the public URLs, not both. We therefore wire the endpoints explicitly so that
 * frontchannel vs backchannel can diverge while the token {@code iss} claim (produced by Keycloak
 * using its {@code KC_HOSTNAME} = public URL) is still validated against the configured issuer.
 */
@Configuration
public class KeycloakClientRegistrationConfig {

  private static final String REGISTRATION_ID = "keycloak";
  private static final String REALM_PATH = "/realms/";
  private static final String AUTH_PATH = "/protocol/openid-connect/auth";
  private static final String TOKEN_PATH = "/protocol/openid-connect/token";
  private static final String JWKS_PATH = "/protocol/openid-connect/certs";

  @Bean
  ReactiveClientRegistrationRepository reactiveClientRegistrationRepository(
      @Value("${keycloak.issuer-uri:http://keycloak:8080/realms/bff-demo}") String backchannelIssuer,
      @Value("${keycloak.public-issuer-uri:http://localhost:8080/realms/bff-demo}")
          String frontchannelIssuer,
      @Value("${keycloak.client-id:bff-client}") String clientId,
      @Value("${keycloak.client-secret:bff-client-secret}") String clientSecret) {

    String backchannelRealmBase = stripTrailingSlash(backchannelIssuer);
    String frontchannelRealmBase = stripTrailingSlash(frontchannelIssuer);

    ClientRegistration keycloak =
        ClientRegistration.withRegistrationId(REGISTRATION_ID)
            .clientId(clientId)
            .clientSecret(clientSecret)
            .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
            .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
            .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
            .scope("openid", "profile", "email")
            // Frontchannel — the browser is redirected here.
            .authorizationUri(frontchannelRealmBase + AUTH_PATH)
            // Backchannel — the BFF calls these directly.
            .tokenUri(backchannelRealmBase + TOKEN_PATH)
            .jwkSetUri(backchannelRealmBase + JWKS_PATH)
            // userInfoUri is intentionally not set: calling Keycloak's userinfo endpoint over
            // the backchannel would fail, because Keycloak compares the access token's `iss`
            // claim (public URL) against the Host header of the userinfo request (internal
            // docker hostname) and rejects the mismatch. The id_token already contains every
            // claim we need (sub, preferred_username, email), so skipping userinfo is safe.
            .userNameAttributeName("preferred_username")
            // Identifier used to validate the `iss` claim in the id_token. Keycloak always
            // mints tokens with its configured KC_HOSTNAME URL (the frontchannel / browser-facing
            // URL) regardless of which network hop delivered the token request, so the id_token
            // must be validated against that same frontchannel URL.
            .issuerUri(frontchannelRealmBase)
            .providerConfigurationMetadata(
                Map.of(
                    "end_session_endpoint",
                    frontchannelRealmBase + "/protocol/openid-connect/logout"))
            .clientName("Keycloak")
            .build();

    return new InMemoryReactiveClientRegistrationRepository(keycloak);
  }

  private static String stripTrailingSlash(String value) {
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }
}
