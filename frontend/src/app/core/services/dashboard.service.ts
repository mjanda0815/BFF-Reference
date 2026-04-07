import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { DashboardData, UserInfo } from '../models/dashboard.model';

/** Talks to the BFF dashboard endpoint. No business logic — pure transport. */
@Injectable({ providedIn: 'root' })
export class DashboardService {
  private readonly http = inject(HttpClient);

  loadDashboard(): Observable<DashboardData> {
    return this.http.get<DashboardData>('/api/dashboard', {
      withCredentials: true,
    });
  }

  loadUserInfo(): Observable<UserInfo> {
    return this.http.get<UserInfo>('/api/userinfo', { withCredentials: true });
  }
}
