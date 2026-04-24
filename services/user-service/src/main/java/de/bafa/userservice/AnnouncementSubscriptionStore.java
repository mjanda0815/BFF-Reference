package de.bafa.userservice;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory store of announcement subscriptions, keyed by announcement id.
 *
 * <p>The reference keeps state in-process on purpose — the blueprint is about the
 * <em>orchestration</em> of a distributed write, not about persistence. A team adopting this
 * template replaces {@link ConcurrentHashMap} with a shared backing store (Redis, JPA +
 * Postgres, …) without changing the controller or the BFF adapter: only this class swaps.
 *
 * <p><b>Writes are idempotent</b> on the {@code announcementId} key. A duplicate
 * {@link #save(AnnouncementSubscription)} with the same id returns the originally stored
 * record and does <em>not</em> overwrite it. That invariant lets the BFF retry transient
 * network failures on the forward step without risking duplicate side effects — it is the
 * contract the retry loop in {@code DistributedWriteSagaOrchestrator} depends on.
 */
@Component
public class AnnouncementSubscriptionStore {

  private final Map<String, AnnouncementSubscription> byId = new ConcurrentHashMap<>();

  /**
   * Idempotent upsert. If an entry with the given {@code announcementId} already exists, the
   * existing entry is returned unchanged; otherwise the supplied value is stored and returned.
   * Atomic via {@link ConcurrentHashMap#putIfAbsent}.
   */
  public AnnouncementSubscription save(AnnouncementSubscription subscription) {
    AnnouncementSubscription existing =
        byId.putIfAbsent(subscription.announcementId(), subscription);
    return existing != null ? existing : subscription;
  }

  public Optional<AnnouncementSubscription> remove(String announcementId) {
    return Optional.ofNullable(byId.remove(announcementId));
  }

  public Collection<AnnouncementSubscription> all() {
    return byId.values();
  }
}
