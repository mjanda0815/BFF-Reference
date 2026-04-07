package io.janda.bff.domain.model;

import java.util.List;

/** Aggregated dashboard payload returned by the BFF to the SPA. */
public record DashboardData(
    UserProfile profile, NotificationOverview notifications, List<ActivityEvent> activity) {}
