package de.bafa.bff.adapter.client;

import de.bafa.bff.config.WebClientConfig;
import de.bafa.bff.domain.model.UserProfile;
import de.bafa.bff.domain.port.UserServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient-backed adapter implementing {@link UserServicePort}.
 *
 * <p><b>Hexagonal role:</b> this is the outbound adapter that talks HTTP to the downstream
 * user-service. The application layer depends only on the {@code UserServicePort} abstraction,
 * which lets tests swap in a stub without involving Spring or WebClient.
 *
 * <p><b>Bearer token propagation:</b> the access token is threaded through explicitly via the
 * {@code accessToken} parameter rather than a Spring Security exchange filter, because this
 * service is called from the aggregation layer (not a web request) and the token is resolved from
 * the session by {@link de.bafa.bff.application.SessionTokenService}.
 */
@Component
public class UserServiceClient implements UserServicePort {

  private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

  private final WebClient webClient;

  public UserServiceClient(@Qualifier(WebClientConfig.USER_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<UserProfile> getUserProfile(String userId, String accessToken) {
    return webClient
        .get()
        .uri("/api/users/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(UserProfile.class)
        .doOnError(ex -> log.warn("user-service call failed: {}", ex.getMessage()));
  }
}
