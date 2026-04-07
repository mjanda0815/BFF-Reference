package io.janda.bff.domain.model;

import java.util.List;

/** Aggregated notification view: total unread count plus the recent items. */
public record NotificationOverview(int unreadCount, List<Notification> items) {

  /** Returns an empty fallback used when the upstream service is unavailable. */
  public static NotificationOverview empty() {
    return new NotificationOverview(0, List.of());
  }
}
