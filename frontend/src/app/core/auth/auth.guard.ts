import { inject } from '@angular/core';
import { CanActivateFn } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { catchError, map, of } from 'rxjs';
import { UserInfo } from '../models/dashboard.model';

/**
 * Auth guard: probes the BFF /api/userinfo endpoint. If the session is valid
 * the navigation proceeds; otherwise the user is redirected through the BFF
 * to the OIDC login flow.
 */
export const authGuard: CanActivateFn = () => {
  const http = inject(HttpClient);
  return http.get<UserInfo>('/api/userinfo', { withCredentials: true }).pipe(
    map(() => true),
    catchError(() => {
      window.location.href = '/login';
      return of(false);
    }),
  );
};
