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
  // etapa-2-usuarios PR-4 — company-user admin error codes
  // (added in PR-3 backend, surfaced by the interceptor so the
  // team pages can show canonical Spanish copy on failure).
  USER_NOT_FOUND: 'No encontramos a ese usuario',
  INVALID_ROLE: 'Uno o más roles no son válidos',
  USER_ALREADY_DISABLED: 'Este usuario ya está deshabilitado',
  USER_ALREADY_ACTIVE: 'Este usuario ya está activo',
  SELF_EDIT_BLOCKED: 'No podés editarte a vos mismo desde acá',
  SELF_DISABLE_BLOCKED: 'No podés deshabilitar tu propio usuario',
  FIRST_ADMIN_PROTECTED: 'El primer administrador está protegido',
  LAST_ADMIN_PROTECTED: 'La empresa debe tener al menos un administrador activo',
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
 * Builds the {@link ApiHttpError} that the {@code errorInterceptor}
 * re-throws. Projects the original {@code HttpErrorResponse}'s
 * {@code headers} so call sites can read response metadata
 * (e.g. {@code Retry-After} for {@code ACCOUNT_LOCKED} minute
 * count). The {@code HttpHeaders} object exposes
 * {@code .get(name): string | null} which is the shape our
 * typed error contract requires.
 *
 * <p>v1 of the interceptor did not project headers and
 * {@link LoginService.extractErrorCopy} had to fall back to
 * {@code details.retryAfterSeconds} for the ACCOUNT_LOCKED
 * minutes derivation. F1 end-to-end testing of the login
 * account-lockout path revealed that some backends send
 * {@code Retry-After} only as a header (not duplicated into
 * the envelope body), so the fallback was unreliable. This
 * helper fixes that gap by always projecting {@code headers}.
 */
function projectApiHttpError(
  err: HttpErrorResponse,
  envelope: ApiErrorEnvelope | { code: 'NETWORK_ERROR'; message: string },
): {
  status: number;
  statusText: string;
  url?: string;
  error: ApiErrorEnvelope | { code: 'NETWORK_ERROR'; message: string };
  headers: { get(name: string): string | null };
} {
  return {
    status: err.status,
    statusText: err.statusText,
    url: err.url ?? undefined,
    error: envelope,
    headers: err.headers,
  };
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
        return throwError(() => projectApiHttpError(err, NETWORK_ERROR));
      }
      if (!(err instanceof HttpErrorResponse)) {
        return throwError(() => err);
      }

      // 2) Parse the backend envelope (if any).
      const envelope = parseEnvelope(err.error);
      const code = envelope?.error.code;
      const message = envelope?.error.message ?? err.statusText;
      const localized = (code && CODE_TO_COPY[code]) ?? message;

      // 2a) Build the projected envelope. When the backend's
      //     code is one we have canonical Spanish copy for,
      //     translate the message; otherwise pass the
      //     envelope through verbatim (with details preserved).
      const projectedEnvelope: ApiErrorEnvelope =
        envelope
          ? code && CODE_TO_COPY[code]
            ? { error: { code, message: localized, details: envelope.error.details } }
            : envelope
          : { error: { code: 'UNKNOWN', message: localized } };

      // 3) Forced-logout path: a 401 on a refreshable endpoint
      //    when the refresh itself 401s. We detect this case
      //    in the catchError of the refresh call below, not
      //    here, because at this layer we only see the
      //    original 401.
      if (err.status !== 401 || shouldNotRetry(req.url)) {
        return throwError(() => projectApiHttpError(err, projectedEnvelope));
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
            return throwError(() =>
              projectApiHttpError(
                err,
                envelope ?? { error: { code: 'UNAUTHENTICATED', message: 'Sesión expirada' } },
              ),
            );
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
