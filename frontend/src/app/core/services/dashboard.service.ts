import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardResult, UserInfo } from '../models/dashboard.model';

/**
 * Talks to the BFF dashboard endpoint. No business logic — pure transport.
 *
 * `loadDashboard()` returns a `DashboardResult` (data + execution log) so the
 * SPA can render the read-side protocol next to the widgets, mirroring the
 * AnnouncementService on the write side.
 */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  loadDashboard(): Observable<DashboardResult> {
    return this.http.get<DashboardResult>('/api/dashboard', {
      withCredentials: true,
    });
  }

  loadUserInfo(): Observable<UserInfo> {
    return this.http.get<UserInfo>('/api/userinfo', { withCredentials: true });
  }
}
