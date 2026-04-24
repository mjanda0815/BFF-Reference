package de.bafa.userservice;

import jakarta.validation.Valid;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Write and compensation endpoints participating in the distributed-write saga demo.
 *
 * <p>{@link #subscribe} executes the local write step for this service (persist a subscription
 * for the authenticated user). {@link #compensate} undoes it — the BFF orchestrator calls this
 * when a later step in the saga fails. {@code forceFail=true} on the command reproducibly
 * triggers a failure so the demo can exercise the compensation path without relying on network
 * gremlins.
 */
@RestController
@RequestMapping("/api/users/me/announcements")
public class AnnouncementController {

  private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

  private final AnnouncementSubscriptionStore store;

  public AnnouncementController(AnnouncementSubscriptionStore store) {
    this.store = store;
  }

  @PostMapping
  public ResponseEntity<AnnouncementSubscription> subscribe(
      @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AnnouncementCommand command) {
    if (command.forceFail()) {
      log.info(
          "user-service: forceFail flag set for announcement {} — returning 500 to exercise"
              + " compensation",
          command.announcementId());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Simulated failure in user-service");
    }
    AnnouncementSubscription saved =
        store.save(
            new AnnouncementSubscription(
                command.announcementId(),
                principal.getSubject(),
                command.message(),
                Instant.now()));
    log.info("user-service: stored subscription {}", saved.announcementId());
    return ResponseEntity.ok(saved);
  }

  @DeleteMapping("/{announcementId}")
  public ResponseEntity<Void> compensate(
      @AuthenticationPrincipal Jwt principal, @PathVariable String announcementId) {
    boolean removed = store.remove(announcementId).isPresent();
    log.info(
        "user-service: compensation for announcement {} — removed={}", announcementId, removed);
    return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
