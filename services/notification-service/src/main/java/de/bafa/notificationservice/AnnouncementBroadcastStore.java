package de.bafa.notificationservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of announcement broadcasts created by the saga demo.
 *
 * <p><b>Writes are idempotent</b> on the {@code id} key — a retried {@code publish()} with the
 * same {@code announcementId} returns the original broadcast unchanged instead of overwriting
 * it. That invariant is what lets the BFF saga retry transient failures safely.
 */
@Component
public class AnnouncementBroadcastStore {

  private final Map<String, Notification> byId = new ConcurrentHashMap<>();

  /**
   * Idempotent upsert. Duplicate id returns the existing record, no overwrite. Atomic via
   * {@link ConcurrentHashMap#putIfAbsent}.
   */
  public Notification save(Notification broadcast) {
    Notification existing = byId.putIfAbsent(broadcast.id(), broadcast);
    return existing != null ? existing : broadcast;
  }

  public Optional<Notification> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }
}
