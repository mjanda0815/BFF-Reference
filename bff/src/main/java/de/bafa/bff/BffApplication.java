package de.bafa.bff;

import de.bafa.bff.config.BffProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Spring Boot entry point for the Backend-for-Frontend (BFF) application.
 *
 * <p><b>Role in the reference architecture:</b> this module sits between the browser-side SPA and
 * the three downstream microservices. It:
 *
 * <ul>
 *   <li>owns the entire OAuth2 / OIDC Authorization Code Flow against Keycloak,
 *   <li>stores access and refresh tokens server-side in Redis (never in the browser),
 *   <li>exposes a small, frontend-shaped HTTP API consumed via session cookies,
 *   <li>aggregates calls to the downstream services in parallel ({@code Mono.zip}) and
 *   <li>applies CSRF, CORS and session cookie hardening from a single security boundary.
 * </ul>
 *
 * <p><b>For developers copying this blueprint:</b> start by skimming the {@code config} package
 * (security, OAuth2 client registration, Redis, CORS, WebClient). The business logic is
 * deliberately tiny — the value of this module is in the cross-cutting configuration, not in its
 * controllers.
 *
 * <p>{@link EnableConfigurationProperties @EnableConfigurationProperties} binds {@link
 * BffProperties} from {@code application.yml}; all tunables live there so none are hidden in code.
 */
@SpringBootApplication
@EnableConfigurationProperties(BffProperties.class)
public class BffApplication {

  public static void main(String[] args) {
    SpringApplication.run(BffApplication.class, args);
  }
}
