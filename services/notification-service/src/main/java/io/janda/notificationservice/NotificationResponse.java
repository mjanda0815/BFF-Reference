package io.janda.notificationservice;

import java.util.List;

/** Aggregated notification response: count of unread plus list of items. */
public record NotificationResponse(int unreadCount, List<Notification> items) {}
