package de.bafa.bff.domain.model;

import java.time.Instant;

/**
 * One entry of the saga's execution log, as returned to the SPA.
 *
 * <p>The SPA treats this as opaque, server-provided data and renders it verbatim in its log
 * panel. The contract is deliberately narrow — the BFF owns the saga vocabulary; the SPA
 * owns only its rendering.
 */
public record SagaStepEntry(
    Instant timestamp, String step, String phase, String status, String detail) {

  /** Phases used in the saga log: forward step, compensating action, or coordinator event. */
  public static final String PHASE_FORWARD = "forward";

  public static final String PHASE_COMPENSATION = "compensation";
  public static final String PHASE_COORDINATOR = "coordinator";

  /** Status values used in the saga log. */
  public static final String STATUS_STARTED = "started";

  public static final String STATUS_SUCCEEDED = "succeeded";
  public static final String STATUS_FAILED = "failed";
  public static final String STATUS_COMPENSATED = "compensated";
}
