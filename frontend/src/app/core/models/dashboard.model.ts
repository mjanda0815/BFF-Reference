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

export interface UserInfo {
  userId: string;
  displayName: string;
  email: string;
}
