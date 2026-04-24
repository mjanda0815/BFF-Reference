package de.bafa.bff.domain.model;

import java.util.List;

/**
 * Result of a distributed-write saga execution, returned to the SPA.
 *
 * <p>{@code outcome} is one of {@link #OUTCOME_SUCCEEDED}, {@link #OUTCOME_COMPENSATED} or
 * {@link #OUTCOME_FAILED}. {@code log} is the full server-side execution trace the SPA renders
 * in its log panel.
 */
public record AnnouncementSagaResult(
    String announcementId, String outcome, String message, List<SagaStepEntry> log) {

  /** All three forward steps succeeded. */
  public static final String OUTCOME_SUCCEEDED = "succeeded";

  /** A forward step failed and every earlier step was compensated. */
  public static final String OUTCOME_COMPENSATED = "compensated";

  /** A forward step failed <em>and</em> at least one compensation also failed. */
  public static final String OUTCOME_FAILED = "failed";
}
