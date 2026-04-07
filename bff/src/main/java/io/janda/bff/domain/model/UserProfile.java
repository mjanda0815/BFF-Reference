package io.janda.bff.domain.model;

/** User profile aggregated by the BFF. */
public record UserProfile(String userId, String displayName, String role, String avatarUrl) {

  /** Returns an empty / fallback profile used when the upstream service is unavailable. */
  public static UserProfile empty() {
    return new UserProfile("", "", "", "");
  }
}
