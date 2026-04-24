package de.bafa.bff.adapter.client;

import de.bafa.bff.config.WebClientConfig;
import de.bafa.bff.domain.port.NotificationAnnouncementWritePort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/** WebClient-backed adapter for the notification-service announcement write endpoints. */
@Component
public class NotificationAnnouncementWriteClient implements NotificationAnnouncementWritePort {

  private static final Logger log =
      LoggerFactory.getLogger(NotificationAnnouncementWriteClient.class);

  private final WebClient webClient;

  public NotificationAnnouncementWriteClient(
      @Qualifier(WebClientConfig.NOTIFICATION_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<Void> publish(
      String announcementId, String message, boolean forceFail, String accessToken) {
    return webClient
        .post()
        .uri("/api/notifications/me/announcements")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "announcementId", announcementId,
                "title", "Announcement",
                "message", message == null ? "" : message,
                "forceFail", forceFail))
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("notification-service publish failed: {}", ex.getMessage()))
        .then();
  }

  @Override
  public Mono<Void> compensate(String announcementId, String accessToken) {
    return webClient
        .delete()
        .uri("/api/notifications/me/announcements/{id}", announcementId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("notification-service compensate failed: {}", ex.getMessage()))
        .then();
  }
}
