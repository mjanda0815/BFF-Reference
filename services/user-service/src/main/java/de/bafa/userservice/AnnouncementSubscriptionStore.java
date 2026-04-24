package de.bafa.userservice;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of announcement subscriptions, keyed by announcement id.
 *
 * <p>The reference keeps state in-process on purpose — a real system would use a database, but
 * the blueprint is about the <em>orchestration</em> of a distributed write, not about
 * persistence. A team adopting this template replaces {@link ConcurrentHashMap} with Spring
 * Data JPA or similar.
 */
@Component
public class AnnouncementSubscriptionStore {

  private final Map<String, AnnouncementSubscription> byId = new ConcurrentHashMap<>();

  public AnnouncementSubscription save(AnnouncementSubscription subscription) {
    byId.put(subscription.announcementId(), subscription);
    return subscription;
  }

  public Optional<AnnouncementSubscription> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }

  public Collection<AnnouncementSubscription> all() {
    return byId.values();
  }
}
