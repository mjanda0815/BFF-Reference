package de.bafa.activityservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of activity log entries created by the saga demo.
 *
 * <p><b>Writes are idempotent</b> on the {@code id} key. A retried {@code logAnnouncement} with
 * the same announcement id returns the original event unchanged — retry-safe semantics the BFF
 * saga's retry loop relies on.
 */
@Component
public class AnnouncementActivityStore {

  private final Map<String, ActivityEvent> byId = new ConcurrentHashMap<>();

  /**
   * Idempotent upsert. Duplicate id returns the existing record, no overwrite. Atomic via
   * {@link ConcurrentHashMap#putIfAbsent}.
   */
  public ActivityEvent save(ActivityEvent event) {
    ActivityEvent existing = byId.putIfAbsent(event.id(), event);
    return existing != null ? existing : event;
  }

  public Optional<ActivityEvent> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }
}
