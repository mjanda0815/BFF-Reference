package com.example.bff.adapter.client;

import com.example.bff.config.WebClientConfig;
import com.example.bff.domain.model.UserProfile;
import com.example.bff.domain.port.UserServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** WebClient-backed adapter for the user-service. */
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
