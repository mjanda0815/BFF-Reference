package com.example.bff.domain.model;

import java.time.Instant;

/** A single notification entry. */
public record Notification(String id, String title, String message, Instant timestamp) {}
