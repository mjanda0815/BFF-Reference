package de.bafa.bff.domain.port;

import reactor.core.publisher.Mono;

/**
 * Write-side port of the notification-service used by the distributed-write saga.
 *
 * <p>Kept separate from {@link NotificationServicePort} for the same reason as the user-side:
 * read-only callers should not incidentally gain write authority on the downstream service.
 */
public interface NotificationAnnouncementWritePort {

  /**
   * Publishes the announcement as a broadcast notification in the notification-service.
   *
   * @param announcementId shared saga identifier
   * @param message announcement message
   * @param forceFail demo hook — asks the service to return HTTP 500 on purpose
   * @param accessToken bearer token forwarded downstream
   */
  Mono<Void> publish(
      String announcementId, String message, boolean forceFail, String accessToken);

  /** Compensates a previously successful {@link #publish} call. */
  Mono<Void> compensate(String announcementId, String accessToken);
}
