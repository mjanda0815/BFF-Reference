package de.bafa.bff.domain.port;

import reactor.core.publisher.Mono;

/**
 * Write-side port of the user-service used by the distributed-write saga.
 *
 * <p>Kept separate from the read-only {@link UserServicePort} on purpose: a component that only
 * reads from the user-service (e.g. the dashboard aggregator) never gains the ability to mutate
 * it by accident. Hexagonal purity pays for itself exactly here.
 */
public interface UserAnnouncementWritePort {

  /**
   * Executes the local write step in the user-service.
   *
   * @param announcementId shared saga identifier, used as the compensation key
   * @param message announcement message to persist alongside the subscription
   * @param forceFail when true asks the service to reproducibly fail (for the compensation demo)
   * @param accessToken bearer token forwarded to the downstream call
   */
  Mono<Void> subscribe(
      String announcementId, String message, boolean forceFail, String accessToken);

  /** Compensates a previously successful {@link #subscribe} call. */
  Mono<Void> compensate(String announcementId, String accessToken);
}
