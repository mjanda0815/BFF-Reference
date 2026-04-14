package de.bafa.bff.domain.model;

import java.util.List;

/**
 * Aggregated dashboard payload returned by the BFF to the SPA — the shape the frontend actually
 * consumes.
 *
 * <p><b>Why a dedicated aggregate record:</b> the BFF deliberately chooses the shape of the data
 * it serves to the SPA instead of forwarding the downstream DTOs verbatim. That keeps the SPA
 * decoupled from internal service schemas and lets the BFF evolve the frontend contract
 * independently.
 */
public record DashboardData(
    UserProfile profile, NotificationOverview notifications, List<ActivityEvent> activity) {}
