package de.bafa.bff.adapter.client;

import de.bafa.bff.config.WebClientConfig;
import de.bafa.bff.domain.port.UserAnnouncementWritePort;
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
 * WebClient-backed adapter for the user-service announcement write endpoints.
 *
 * <p>Uses the shared {@link WebClientConfig#USER_SERVICE} WebClient bean — same timeouts, same
 * error translation as every other user-service call in the BFF. The saga-level concerns
 * (ordering, compensation) live in the orchestrator; this class only knows HTTP.
 *
 * <p><b>Scalability note for downstream adoption.</b> The reference uses in-memory state in the
 * downstream services, so compensation only works when the write <em>and</em> the delete hit
 * the same instance. A real multi-replica deployment replaces the in-memory map with a
 * persistent store (Postgres/Redis) and an idempotency key per saga id.
 */
@Component
public class UserAnnouncementWriteClient implements UserAnnouncementWritePort {

  private static final Logger log = LoggerFactory.getLogger(UserAnnouncementWriteClient.class);

  private final WebClient webClient;

  public UserAnnouncementWriteClient(
      @Qualifier(WebClientConfig.USER_SERVICE) WebClient webClient) {
    this.webClient = webClient;
  }

  @Override
  public Mono<Void> subscribe(
      String announcementId, String message, boolean forceFail, String accessToken) {
    return webClient
        .post()
        .uri("/api/users/me/announcements")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            Map.of(
                "announcementId", announcementId,
                "message", message == null ? "" : message,
                "forceFail", forceFail))
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("user-service subscribe failed: {}", ex.getMessage()))
        .then();
  }

  @Override
  public Mono<Void> compensate(String announcementId, String accessToken) {
    return webClient
        .delete()
        .uri("/api/users/me/announcements/{id}", announcementId)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .retrieve()
        .toBodilessEntity()
        .doOnError(ex -> log.warn("user-service compensate failed: {}", ex.getMessage()))
        .then();
  }
}
