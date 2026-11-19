import { HttpErrorResponse, HttpInterceptorFn, HttpRequest } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError, Observable, shareReplay, finalize } from 'rxjs';
import { AuthService } from './auth.service';
import { ToastService } from './toast.service';

let refreshing$: Observable<unknown> | null = null;

function withAuth(req: HttpRequest<unknown>, auth: AuthService): HttpRequest<unknown> {
  const headers: Record<string, string> = {};
  if (auth.accessToken) headers['Authorization'] = 'Bearer ' + auth.accessToken;
  const t = auth.tenantOverride();
  if (t) headers['X-Tenant-Id'] = t;
  return req.clone({ setHeaders: headers });
}

/** Adds the bearer token, transparently refreshes an expired access token once, and surfaces API errors as toasts. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const toast = inject(ToastService);
  const isAuthCall = req.url.includes('/api/v1/auth/login') || req.url.includes('/api/v1/auth/refresh');
  const isPublic = req.url.includes('/api/v1/public/') || req.url.startsWith('/i18n/');
  return next(isPublic ? req : withAuth(req, auth)).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && !isAuthCall && !isPublic && auth.refreshToken) {
        refreshing$ ??= auth.refresh().pipe(shareReplay(1), finalize(() => (refreshing$ = null)));
        return refreshing$.pipe(
          switchMap(() => next(withAuth(req, auth))),
          catchError((e) => {
            auth.logout();
            return throwError(() => e);
          }),
        );
      }
      if (err.status === 401 && !isAuthCall && !isPublic) auth.logout();
      if (!isAuthCall && err.status !== 401 && err.status !== 404 && req.method !== 'GET') {
        const body = err.error as { detail?: string; errors?: Record<string, string> } | null;
        const detail = body?.errors ? Object.entries(body.errors).map(([k, v]) => `${k}: ${v}`).join(' · ') : body?.detail;
        toast.error(detail || err.message);
      } else if (err.status >= 500) {
        toast.error((err.error as { detail?: string })?.detail || 'Server error');
      }
      return throwError(() => err);
    }),
  );
};
