package de.bafa.userservice;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource server security configuration validating JWTs against Keycloak — the canonical template
 * for any downstream service in this reference stack.
 *
 * <p><b>Key decisions:</b>
 *
 * <ul>
 *   <li><b>Stateless sessions.</b> Downstream services never hold a session — every request must
 *       carry a valid bearer token. That keeps them horizontally trivial to scale and removes
 *       session-pinning concerns.
 *   <li><b>CSRF disabled.</b> CSRF is a cookie-authentication problem; bearer-only APIs are
 *       immune by design. The BFF handles CSRF for the SPA; this service does not need to.
 *   <li><b>Health/info endpoints open.</b> Required for container orchestration liveness checks.
 *       Everything else requires a valid JWT.
 *   <li><b>Split JWK / issuer wiring.</b> The {@link JwtDecoder} is wired manually so the JWKS
 *       can be loaded from Keycloak's <em>internal</em> URL (reachable on the docker network)
 *       while the {@code iss} claim is validated against the <em>public</em> URL that Keycloak
 *       actually stamps into the token. Using Spring Boot's auto-configured {@code issuer-uri}
 *       does not work here because OIDC discovery returns an issuer that never matches both
 *       sides at once.
 * </ul>
 *
 * <p>The {@code @ConditionalOnMissingBean} on the decoder lets tests replace it with a
 * {@code mock(JwtDecoder.class)} to avoid contacting Keycloak during unit testing.
 */
@Configuration
public class SecurityConfig {

  @Bean
  SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http.csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> {}));
    return http.build();
  }

  @Bean
  @ConditionalOnMissingBean(JwtDecoder.class)
  JwtDecoder jwtDecoder(
      @Value("${keycloak.jwk-set-uri}") String jwkSetUri,
      @Value("${keycloak.expected-issuer}") String expectedIssuer) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
    OAuth2TokenValidator<Jwt> validator = JwtValidators.createDefaultWithIssuer(expectedIssuer);
    decoder.setJwtValidator(validator);
    return decoder;
  }
}
