package de.bafa.bff.adapter.client;

import de.bafa.bff.config.WebClientConfig;
import de.bafa.bff.domain.port.ActivityAnnouncementWritePort;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * WebClient-backed adapter for the activity-service announcement write endpoints.
 *
 * <p>Identical in structure to {@link UserAnnouncementWriteClient} and
 * {@link NotificationAnnouncementWriteClient} — only the URL and payload fields differ. The
 * activity service records the announcement as an audit-log event; the {@code message} from
 * the saga maps to the event's {@code resource} field on the service side.
 *
 * <p><b>Für die Übernahme in ein neues Produkt:</b> Die drei Write-Adapter sind absichtlich
 * strukturgleich gehalten — beim Kopieren für einen neuen Saga-Schritt reicht es, diesen
 * File als Muster zu übernehmen und nur die URI-Pfade, den Port-Typ und den
 * {@code @Qualifier} für das neue Service anzupassen.
 */
@Component
public class ActivityAnnouncementWriteClient implements ActivityAnnouncementWritePort {

  private static final Logger log =
      LoggerFactory.getLogger(ActivityAnnouncementWriteClient.class);

  private final WebClient webClient;

  public ActivityAnnouncementWriteClient(
      @Qualifier(WebClientConfig.ACTIVITY_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<Void> logAnnouncement(
      String announcementId, String message, boolean forceFail, String accessToken) {
    return webClient
        .post()
        .uri("/api/activity/me/announcements")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "announcementId", announcementId,
                "message", message == null ? "" : message,
                "forceFail", forceFail))
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("activity-service logAnnouncement failed: {}", ex.getMessage()))
        .then();
  }

  @Override
  public Mono<Void> compensate(String announcementId, String accessToken) {
    return webClient
        .delete()
        .uri("/api/activity/me/announcements/{id}", announcementId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("activity-service compensate failed: {}", ex.getMessage()))
        .then();
  }
}
