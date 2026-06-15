import { HttpErrorResponse, HttpInterceptorFn, HttpRequest, HttpHandlerFn } from '@angular/common/http';
import { inject, Injector } from '@angular/core';
import { Observable, of, throwError, from, switchMap, catchError } from 'rxjs';
import { ApiErrorEnvelope } from '../types';
import { AuthStore} from '../state/auth-store';
import {TenantStore} from '../state/tenant-store';
import {Router} from '@angular/router';

/**
 * The set of URL paths the {@code errorInterceptor} treats as
 * "auth bootstrap" — a 401 on these endpoints is NOT retried
 * with a refresh because the user is logging in / registering
 * / actively refreshing already. Everywhere else, a 401
 * triggers exactly one refresh-and-retry attempt; if the
 * refresh also responds 401, the interceptor performs a
 * forced logout (clears both stores, navigates to /login with
 * the returnUrl preserved).
 */
const NO_RETRY_PATHS = [
  '/api/v1/auth/login',
  '/api/v1/auth/register',
  '/api/v1/auth/refresh',
];

/**
 * Path prefixes the {@code errorInterceptor} ALSO does not
 * retry — these are the platform paths which have their own
 * session in PR7; v1 of the client never sends platform
 * cookies to a company path, so a 401 here is treated as a
 * real auth failure (not a refreshable one).
 */
const NO_RETRY_PREFIXES = ['/api/v1/platform/'];

/**
 * Canonical Spanish copy for the user-facing error envelope.
 * When the backend's {@code error.code} is one of these the
 * interceptor returns a re-thrown {@code HttpErrorResponse}
 * with the {@code message} translated to the canonical copy.
 * The login / register feature components surface this
 * {@code message} directly in the live region.
 */
const CODE_TO_COPY: Record<string, string> = {
  INVALID_CREDENTIALS: 'Las credenciales no son correctas',
  ACCOUNT_DISABLED: 'Tu cuenta está deshabilitada. Contactanos a soporte.',
  ACCOUNT_LOCKED: 'Demasiados intentos. Probá de nuevo en unos minutos',
  VALIDATION_ERROR: 'Revisá los datos ingresados',
  RATE_LIMIT_EXCEEDED: 'Hiciste demasiados intentos. Esperá un momento.',
  FORBIDDEN_SCOPE: 'No tenés permisos para esta acción',
  NETWORK_ERROR: 'No pudimos conectarnos. Reintentá',
};

const NETWORK_ERROR = {
  error: {
    code: 'NETWORK_ERROR' as const,
    message: 'No pudimos conectarnos. Reintentá',
  },
};

/**
 * Returns true when the URL path is one of the no-retry auth
 * bootstrap endpoints.
 */
function shouldNotRetry(path: string): boolean {
  if (NO_RETRY_PATHS.includes(path)) return true;
  return NO_RETRY_PREFIXES.some((p) => path.startsWith(p));
}

/**
 * Parses the backend's canonical error envelope. Returns the
 * envelope if the body matches the expected shape, or null
 * for a body that is not in the envelope format (caller treats
 * null as "use the statusText + default copy").
 */
function parseEnvelope(body: unknown): ApiErrorEnvelope | null {
  if (body === null || typeof body !== 'object') return null;
  const b = body as Record<string, unknown>;
  if (typeof b['error'] !== 'object' || b['error'] === null) return null;
  const e = b['error'] as Record<string, unknown>;
  const code = e['code'];
  const message = e['message'];
  if (typeof code !== 'string' || typeof message !== 'string') return null;
  const rawDetails = e['details'];
  const details = typeof rawDetails === 'object' && rawDetails !== null
    ? (rawDetails as Record<string, string>)
    : undefined;
  return { error: { code, message, details } };
}

/**
 * {@code errorInterceptor} — parses the backend's canonical
 * error envelope, normalises network failures, and performs
 * the 401 refresh-and-retry flow. A 401 from a non-bootstrap
 * endpoint triggers exactly one {@code POST /auth/refresh}
 * call; on refresh success the original request is retried
 * with the new cookies applied. A refresh 401 (or any
 * non-200) triggers a forced logout: clear both stores and
 * navigate to {@code /login?returnUrl=...}.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const injector = inject(Injector);
  return next(req).pipe(
    catchError((err: unknown) => {
      // 1) Network failure: status === 0 with a ProgressEvent
      //    error is a connectivity problem, not a server error.
      if (err instanceof HttpErrorResponse && err.status === 0) {
        return throwError(() => ({
          ...err,
          error: NETWORK_ERROR,
        }));
      }
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }

      // 2) Parse the backend envelope (if any).
      const envelope = parseEnvelope(err.error);
      const code = envelope?.error.code;
      const message = envelope?.error.message ?? err.statusText;
      const details = envelope?.error.details;
      const localized = (code && CODE_TO_COPY[code]) ?? message;

      // 3) Forced-logout path: a 401 on a refreshable endpoint
      //    when the refresh itself 401s. We detect this case
      //    in the catchError of the refresh call below, not
      //    here, because at this layer we only see the
      //    original 401.
      if (err.status !== 401 || shouldNotRetry(req.url)) {
        return throwError(() => ({
          ...err,
          status: err.status,
          error: envelope ?? { error: { code: 'UNKNOWN', message: localized } },
        }));
      }

      // 4) 401 on a refreshable endpoint: try exactly one
      //    refresh, then retry the original request.
      return from(
        fetch('/api/v1/auth/refresh', {
          method: 'POST',
          credentials: 'include',
          headers: { 'Content-Type': 'application/json' },
        })
      ).pipe(
        switchMap((refreshRes) => {
          if (refreshRes.status !== 200) {
            // Forced logout: clear both stores + navigate.
            const auth = injector.get(AuthStore);
            const tenant = injector.get(TenantStore);
            const router = injector.get(Router);
            auth.clear();
            tenant.clear();
            const returnUrl = router.url;
            router.navigate(['/login'], {
              queryParams: { returnUrl: returnUrl || '/' },
            });
            return throwError(() => ({
              ...err,
              error: envelope ?? { error: { code: 'UNAUTHENTICATED', message: 'Sesión expirada' } },
            }));
          }
          // Refresh succeeded — the new cookies are now in the
          // jar. Retry the original request.
          return next(req);
        }),
        catchError((refreshErr) => {
          // The fetch above itself failed (network). Treat it
          // the same as a non-200 refresh: forced logout.
          const auth = injector.get(AuthStore);
          const tenant = injector.get(TenantStore);
          auth.clear();
          tenant.clear();
          return throwError(() => refreshErr);
        }),
      );
    }),
  );
};
