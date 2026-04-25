export interface UserProfile {
  userId: string;
  displayName: string;
  role: string;
  avatarUrl: string;
}

export interface Notification {
  id: string;
  title: string;
  message: string;
  timestamp: string;
}

export interface NotificationOverview {
  unreadCount: number;
  items: Notification[];
}

export interface ActivityEvent {
  id: string;
  action: string;
  resource: string;
  timestamp: string;
}

export interface DashboardData {
  profile: UserProfile;
  notifications: NotificationOverview;
  activity: ActivityEvent[];
}

/**
 * One entry of the dashboard's read-side execution log, mirrored from the BFF.
 *
 * Same shape as the saga panel's SagaStepEntry — the SPA renders both with the
 * same row layout. Backend uses two separate records (AggregationStepEntry vs.
 * SagaStepEntry) for semantic clarity (no compensation phase on read).
 */
export interface AggregationStepEntry {
  readonly timestamp: string;
  readonly step: string;
  readonly phase: 'forward' | 'coordinator';
  readonly status: 'started' | 'succeeded' | 'failed';
  readonly detail: string;
}

/**
 * Server-authored bundle of dashboard data + the execution protocol of how the
 * BFF assembled it. Symmetric to AnnouncementSagaResult on the write path.
 */
export interface DashboardResult {
  readonly data: DashboardData;
  readonly log: readonly AggregationStepEntry[];
}

export interface UserInfo {
  userId: string;
  displayName: string;
  email: string;
}
