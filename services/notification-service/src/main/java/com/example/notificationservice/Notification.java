package com.example.notificationservice;

import java.time.Instant;

/** A notification entry returned to the BFF. */
public record Notification(String id, String title, String message, Instant timestamp) {}
