package de.bafa.bff.domain.model;

/**
 * User profile as returned to the SPA.
 *
 * <p>The {@link #empty()} factory is used by the aggregation service's partial-failure fallback —
 * see {@link de.bafa.bff.application.DashboardAggregationService}. Keeping the fallback here
 * rather than at the call site ensures every caller uses the same null-object representation.
 */
public record UserProfile(String userId, String displayName, String role, String avatarUrl) {

  /** Empty / fallback profile used when the upstream user-service is unavailable. */
  public static UserProfile empty() {
    return new UserProfile("", "", "", "");
  }
}
