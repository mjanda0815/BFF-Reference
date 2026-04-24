package de.bafa.notificationservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory store of announcement broadcasts created by the saga demo. */
@Component
public class AnnouncementBroadcastStore {

  private final Map<String, Notification> byId = new ConcurrentHashMap<>();

  public Notification save(Notification broadcast) {
    byId.put(broadcast.id(), broadcast);
    return broadcast;
  }

  public Optional<Notification> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }
}
