package de.bafa.bff.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

/**
 * Exercises both branches of the {@code stripTrailingSlash} helper inside {@link
 * KeycloakClientRegistrationConfig} by letting the bean build registrations from issuer URIs with
 * and without a trailing slash. The produced {@link ClientRegistration} must contain identical
 * authorization/token/JWKS URIs regardless of the input form.
 */
class KeycloakClientRegistrationConfigTest {

  private final KeycloakClientRegistrationConfig config = new KeycloakClientRegistrationConfig();

  @Test
  void trailingSlashOnIssuerUrisDoesNotLeakIntoRegistration() {
    ClientRegistration withSlash =
        config
            .reactiveClientRegistrationRepository(
                "http://keycloak:8080/realms/bff-demo/",
                "http://localhost:8080/realms/bff-demo/",
                "bff-client",
                "secret")
            .findByRegistrationId("keycloak")
            .block();
    ClientRegistration withoutSlash =
        config
            .reactiveClientRegistrationRepository(
                "http://keycloak:8080/realms/bff-demo",
                "http://localhost:8080/realms/bff-demo",
                "bff-client",
                "secret")
            .findByRegistrationId("keycloak")
            .block();

    assertThat(withSlash).isNotNull();
    assertThat(withoutSlash).isNotNull();
    assertThat(withSlash.getProviderDetails().getAuthorizationUri())
        .isEqualTo(withoutSlash.getProviderDetails().getAuthorizationUri())
        .doesNotContain("//protocol");
    assertThat(withSlash.getProviderDetails().getTokenUri())
        .isEqualTo(withoutSlash.getProviderDetails().getTokenUri())
        .doesNotContain("//protocol");
    assertThat(withSlash.getProviderDetails().getJwkSetUri())
        .isEqualTo(withoutSlash.getProviderDetails().getJwkSetUri())
        .doesNotContain("//protocol");
    assertThat(withSlash.getProviderDetails().getIssuerUri())
        .isEqualTo(withoutSlash.getProviderDetails().getIssuerUri())
        .doesNotEndWith("/");
  }
}
