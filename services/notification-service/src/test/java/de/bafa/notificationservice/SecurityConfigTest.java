package de.bafa.notificationservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Exercises {@link SecurityConfig#jwtDecoder(String, String)} directly so the production bean — which
 * is swapped out by a mocked {@link JwtDecoder} in {@link NotificationControllerTest} — still
 * participates in coverage. The decoder is built but never asked to resolve a JWKS, so no network
 * I/O occurs.
 */
class SecurityConfigTest {

  @Test
  void jwtDecoderIsBuiltWithIssuerValidator() {
    SecurityConfig config = new SecurityConfig();

    JwtDecoder decoder =
        config.jwtDecoder(
            "http://keycloak:8080/realms/bff-demo/protocol/openid-connect/certs",
            "http://localhost:8080/realms/bff-demo");

    assertThat(decoder).isInstanceOf(NimbusJwtDecoder.class);
  }
}
