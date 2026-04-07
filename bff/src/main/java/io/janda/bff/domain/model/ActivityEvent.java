package io.janda.bff.domain.model;

import java.time.Instant;

/** A single activity / audit event. */
public record ActivityEvent(String id, String action, String resource, Instant timestamp) {}
