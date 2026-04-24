package de.bafa.bff.domain.model;

/**
 * Command issued by the SPA to the BFF to execute a distributed-write saga across the three
 * downstream services.
 *
 * <p>{@code failAt} is an optional hint for the didactic demo: when set to the name of a saga
 * step ({@code "user"}, {@code "notification"} or {@code "activity"}), that step is asked to
 * fail reproducibly so users can observe the compensation path in the UI. {@code null} means
 * "run to completion".
 */
public record AnnouncementCommand(String message, String failAt) {

  /** Announcement-message fallback used when the SPA does not provide one. */
  public static final String DEFAULT_MESSAGE = "New company-wide announcement";

  public String resolvedMessage() {
    return message == null || message.isBlank() ? DEFAULT_MESSAGE : message;
  }

  /** True when the demo requested a reproducible failure in the given saga step. */
  public boolean shouldFail(String stepKey) {
    return failAt != null && failAt.equalsIgnoreCase(stepKey);
  }
}
