package de.bafa.bff.domain.port;

import reactor.core.publisher.Mono;

/**
 * Write-side port of the activity-service used by the distributed-write saga.
 *
 * <p>Kept separate from {@link ActivityServicePort}: the dashboard aggregator only needs to read
 * activity events, so it never sees this port and cannot accidentally log events.
 */
public interface ActivityAnnouncementWritePort {

  /**
   * Records an ANNOUNCEMENT activity event in the activity-service.
   *
   * @param announcementId shared saga identifier, also used as the event id
   * @param message announcement message — stored as the {@code resource} field of the activity
   * @param forceFail demo hook — asks the service to return HTTP 500 on purpose
   * @param accessToken bearer token forwarded downstream
   */
  Mono<Void> logAnnouncement(
      String announcementId, String message, boolean forceFail, String accessToken);

  /** Compensates a previously successful {@link #logAnnouncement} call. */
  Mono<Void> compensate(String announcementId, String accessToken);
}
