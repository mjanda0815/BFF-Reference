import { HttpInterceptorFn } from '@angular/common/http';
import { catchError, throwError } from 'rxjs';

/**
 * Auth interceptor: ensures every outgoing request carries the session cookie
 * and reacts to 401 by sending the user to the BFF login flow. The frontend
 * never touches an OAuth/OIDC token — that lives only in the BFF session.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const withCredentials = req.clone({ withCredentials: true });
  return next(withCredentials).pipe(
    catchError((error: unknown) => {
      if (
        typeof error === 'object' &&
        error !== null &&
        'status' in error &&
        (error as { status: number }).status === 401
      ) {
        // Hard-redirect via the BFF so the OIDC code-flow can begin.
        window.location.href = '/login';
      }
      return throwError(() => error);
    }),
  );
};
