package de.bafa.activityservice;

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
 * <p>{@link #logAnnouncement} appends an ANNOUNCEMENT activity event; {@link #compensate}
 * removes it when the saga is rolled back.
 */
@RestController
@RequestMapping("/api/activity/me/announcements")
public class AnnouncementController {

  private static final Logger log = LoggerFactory.getLogger(AnnouncementController.class);

  private final AnnouncementActivityStore store;

  public AnnouncementController(AnnouncementActivityStore store) {
    this.store = store;
  }

  @PostMapping
  public ResponseEntity<ActivityEvent> logAnnouncement(
      @AuthenticationPrincipal Jwt principal, @Valid @RequestBody AnnouncementCommand command) {
    if (command.forceFail()) {
      log.info(
          "activity-service: forceFail flag set for announcement {} — returning 500",
          command.announcementId());
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Simulated failure in activity-service");
    }
    String resource =
        command.message() != null && !command.message().isBlank()
            ? command.message()
            : "announcement";
    ActivityEvent saved =
        store.save(
            new ActivityEvent(command.announcementId(), "ANNOUNCEMENT", resource, Instant.now()));
    log.info(
        "activity-service: stored activity {} for user {}", saved.id(), principal.getSubject());
    return ResponseEntity.ok(saved);
  }

  @DeleteMapping("/{announcementId}")
  public ResponseEntity<Void> compensate(
      @AuthenticationPrincipal Jwt principal, @PathVariable String announcementId) {
    boolean removed = store.remove(announcementId).isPresent();
    log.info(
        "activity-service: compensation for announcement {} — removed={}",
        announcementId,
        removed);
    return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
  }
}
