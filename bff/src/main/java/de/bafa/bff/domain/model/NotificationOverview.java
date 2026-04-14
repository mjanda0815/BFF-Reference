package de.bafa.bff.domain.model;

import java.util.List;

/**
 * Aggregated notification view returned to the SPA: total unread count plus the most recent items.
 *
 * <p>See {@link UserProfile#empty()} for the rationale of the {@link #empty()} factory.
 */
public record NotificationOverview(int unreadCount, List<Notification> items) {

  /** Empty fallback used when the upstream notification-service is unavailable. */
  public static NotificationOverview empty() {
    return new NotificationOverview(0, List.of());
  }
}
