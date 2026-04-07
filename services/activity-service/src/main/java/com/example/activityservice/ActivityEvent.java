package com.example.activityservice;

import java.time.Instant;

/** A single activity / audit log event. */
public record ActivityEvent(String id, String action, String resource, Instant timestamp) {}
