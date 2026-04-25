package de.bafa.bff.domain.model;

import java.time.Instant;

/**
 * One entry of the dashboard aggregation's execution log, returned to the SPA alongside the
 * data so the frontend can render a step-by-step protocol.
 *
 * <p>Structurally identical to {@link SagaStepEntry} — same five fields, same opaque-from-SPA
 * contract — but kept as a distinct record for semantic clarity: aggregation reads do not
 * compensate, so the {@link #PHASE_FORWARD forward} and {@link #PHASE_COORDINATOR coordinator}
 * phases are the only ones used. A team building a new read-side aggregation in a forked
 * product reuses this record; a team building a new write-side saga reuses
 * {@code SagaStepEntry}.
 *
 * <p><b>Für die Übernahme in ein neues Produkt:</b> Wenn die SPA die beiden Logs in einer
 * gemeinsamen Komponente rendern soll, kann man auf der Frontend-Seite einen gemeinsamen
 * TypeScript-Typ verwenden — die JSON-Form ist identisch. Auf Backend-Seite die Records aber
 * getrennt halten, damit Read- und Write-Pfad nicht versehentlich Phasen mischen
 * (z. B. eine "compensation"-Zeile in einem Read-Log, die fachlich unsinnig wäre).
 */
public record AggregationStepEntry(
    Instant timestamp, String step, String phase, String status, String detail) {

  /** Phases used in the aggregation log: forward step or coordinator-level event. */
  public static final String PHASE_FORWARD = "forward";

  public static final String PHASE_COORDINATOR = "coordinator";

  /** Status values used in the aggregation log. */
  public static final String STATUS_STARTED = "started";

  public static final String STATUS_SUCCEEDED = "succeeded";
  public static final String STATUS_FAILED = "failed";
}
