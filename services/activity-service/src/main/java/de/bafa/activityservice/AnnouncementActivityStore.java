package de.bafa.activityservice;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory store of activity log entries created by the saga demo. */
@Component
public class AnnouncementActivityStore {

  private final Map<String, ActivityEvent> byId = new ConcurrentHashMap<>();

  public ActivityEvent save(ActivityEvent event) {
    byId.put(event.id(), event);
    return event;
  }

  public Optional<ActivityEvent> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }
}
