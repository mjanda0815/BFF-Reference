/**
 * Client-side shape of the saga result returned by POST /api/announcements.
 *
 * The BFF owns every decision (step order, compensation, log content); this
 * module only types the payload so the SPA can render it as-is.
 */
export type SagaOutcome = 'succeeded' | 'compensated' | 'failed';

export type SagaPhase = 'forward' | 'compensation' | 'coordinator';

export type SagaStepStatus =
  | 'started'
  | 'succeeded'
  | 'failed'
  | 'compensated'
  | SagaOutcome;

export interface SagaStepEntry {
  readonly timestamp: string;
  readonly step: string;
  readonly phase: SagaPhase;
  readonly status: SagaStepStatus;
  readonly detail: string;
}

export interface AnnouncementSagaResult {
  readonly announcementId: string;
  readonly outcome: SagaOutcome;
  readonly message: string;
  readonly log: readonly SagaStepEntry[];
}

/** Allowed values for the optional failAt demo knob, mirrored from the BFF. */
export type FailAtTarget = 'none' | 'user' | 'notification' | 'activity';

export interface AnnouncementCommand {
  readonly message: string;
  /** Omitted from the payload when `'none'`. */
  readonly failAt: FailAtTarget;
}
