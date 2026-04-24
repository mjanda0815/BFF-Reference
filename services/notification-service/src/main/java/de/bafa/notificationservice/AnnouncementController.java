package de.bafa.notificationservice;

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
 * Write and compensation endpoints for the distributed-write saga demo.
 *
 * <p>{@link #publish} creates a broadcast notification for the announcement; {@link #compensate}
 * removes it again when the saga is rolled back.
 */
@RestController
@RequestMapping("/api/notifications/me/announcements")
public class AnnouncementController {

  private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

  private final AnnouncementBroadcastStore store;

  public AnnouncementController(AnnouncementBroadcastStore store) {
    this.store = store;
  }

  @PostMapping
  public ResponseEntity<Notification> publish(
      @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AnnouncementCommand command) {
    if (command.forceFail()) {
      log.info(
          "notification-service: forceFail flag set for announcement {} — returning 500",
          command.announcementId());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Simulated failure in notification-service");
    }
    String title = command.title() != null ? command.title() : "Announcement";
    String message = command.message() != null ? command.message() : "New announcement published.";
    Notification saved =
        store.save(new Notification(command.announcementId(), title, message, Instant.now()));
    log.info(
        "notification-service: stored broadcast {} for user {}",
        saved.id(),
        principal.getSubject());
    return ResponseEntity.ok(saved);
  }

  @DeleteMapping("/{announcementId}")
  public ResponseEntity<Void> compensate(
      @AuthenticationPrincipal Jwt principal, @PathVariable String announcementId) {
    boolean removed = store.remove(announcementId).isPresent();
    log.info(
        "notification-service: compensation for announcement {} — removed={}",
        announcementId,
        removed);
    return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
