package io.janda.bff.adapter.client;

import io.janda.bff.config.WebClientConfig;
import io.janda.bff.domain.model.ActivityEvent;
import io.janda.bff.domain.port.ActivityServicePort;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** WebClient-backed adapter for the activity-service. */
@Component
public class ActivityServiceClient implements ActivityServicePort {

  private static final Logger log = LoggerFactory.getLogger(ActivityServiceClient.class);
  private static final ParameterizedTypeReference<List<ActivityEvent>> EVENT_LIST =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;

  public ActivityServiceClient(@Qualifier(WebClientConfig.ACTIVITY_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<List<ActivityEvent>> getRecentActivity(String userId, String accessToken) {
    return webClient
        .get()
        .uri("/api/activity/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(EVENT_LIST)
        .doOnError(ex -> log.warn("activity-service call failed: {}", ex.getMessage()));
  }
}
