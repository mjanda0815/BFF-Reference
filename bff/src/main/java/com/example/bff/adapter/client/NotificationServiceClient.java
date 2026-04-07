package com.example.bff.adapter.client;

import com.example.bff.config.WebClientConfig;
import com.example.bff.domain.model.NotificationOverview;
import com.example.bff.domain.port.NotificationServicePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** WebClient-backed adapter for the notification-service. */
@Component
public class NotificationServiceClient implements NotificationServicePort {

  private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

  private final WebClient webClient;

  public NotificationServiceClient(
      @Qualifier(WebClientConfig.NOTIFICATION_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<NotificationOverview> getNotifications(String userId, String accessToken) {
    return webClient
        .get()
        .uri("/api/notifications/me")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .accept(MediaType.APPLICATION_JSON)
        .retrieve()
        .bodyToMono(NotificationOverview.class)
        .doOnError(ex -> log.warn("notification-service call failed: {}", ex.getMessage()));
  }
}
