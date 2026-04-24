package de.bafa.userservice;

import java.time.Instant;

/** A user-local record that this user has been subscribed to an announcement. */
public record AnnouncementSubscription(
    String announcementId, String userId, String message, Instant createdAt) {}
