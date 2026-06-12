import { Injectable, computed, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable } from 'rxjs';

import { AuthService } from './auth.service';
import { ApiHttpError, LoginRequest, LoginResponse } from '../types';

/**
 * Shape returned by {@code extractErrorCopy}. The component
 * binds the message into the {@code aria-live} region and,
 * for {@code ACCOUNT_LOCKED} copies, formats the minutes
 * into the canonical "Probá de nuevo en {X} minutos" text.
 */
export interface ErrorCopy {
  code: string;
  message: string;
  minutes?: number;
}

/**
 * Presentation-layer helper for the {@code /login} page. The
 * HTTP side is owned by {@code AuthService.login} — this
 * service composes that with the page's auxiliary concerns:
 *
 * <ul>
 *   <li>Reading the {@code returnUrl} query parameter once at
 *       construction and validating it is a same-origin
 *       relative path (an open-redirect guard: a {@code //}
 *       prefix or an absolute URL would let an attacker
 *       redirect the user to a third-party site after
 *       successful login).</li>
 *   <li>Mapping the post-interceptor {@code ApiHttpError}
 *       into a stable Spanish copy with the
 *       {@code ACCOUNT_LOCKED} minutes value when the
 *       backend provides {@code details.retryAfterSeconds} or
 *       a {@code Retry-After} response header.</li>
 * </ul>
 *
 * The service does NOT call the network itself — {@code submit}
 * delegates to {@code AuthService.login} and returns the same
 * observable so the component can subscribe + navigate. The
 * service has no router reference; navigation is a component
 * concern.
 */
@Injectable({ providedIn: 'root' })
export class LoginService {
  private readonly authService = inject(AuthService);
  private readonly route = inject(ActivatedRoute);

  private readonly _returnUrl = signal<string>(
    this.validateReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')),
  );

  /**
   * The validated {@code returnUrl} the component should
   * navigate to after a successful login. Always a same-origin
   * path that starts with a single {@code /}; falls back to
   * {@code /dashboard} when the query parameter is missing or
   * fails the open-redirect guard.
   */
  readonly safeReturnUrl = computed(() => this._returnUrl());

  /**
   * {@code POST /api/v1/auth/login} via {@code AuthService}.
   * The component subscribes and navigates to
   * {@code safeReturnUrl()} on success.
   */
  submit(creds: LoginRequest): Observable<LoginResponse> {
    return this.authService.login(creds);
  }

  /**
   * Pure function: extract a stable Spanish copy + an
   * optional minutes value from an {@code ApiHttpError}
   * (the post-{@code errorInterceptor} shape).
   *
   * Rules:
   * - {@code ACCOUNT_LOCKED} with
   *   {@code details.retryAfterSeconds}: minutes =
   *   {@code Math.ceil(seconds / 60)}.
   * - {@code ACCOUNT_LOCKED} without details, with a
   *   {@code Retry-After} response header: same derivation
   *   from the header value (interpreted as seconds).
   * - {@code ACCOUNT_LOCKED} without both: minutes omitted;
   *   the caller falls back to "en unos minutos" copy.
   * - All other codes: minutes omitted; the envelope
   *   {@code message} is returned verbatim so the
   *   interceptor's localized copy (or the server's
   *   Spanish fallback) reaches the user untouched.
   */
  extractErrorCopy(thrown: ApiHttpError): ErrorCopy {
    // The interceptor's NETWORK_ERROR shape has no
    // .error.error.code — treat it like any other
    // non-envelope error and surface the message.
    const inner = this.readEnvelope(thrown);
    const code = inner?.code ?? 'UNKNOWN';
    const message = inner?.message ?? '';

    if (code !== 'ACCOUNT_LOCKED') {
      return { code, message };
    }

    const minutes = this.lockoutMinutes(thrown, inner?.details);
    return minutes === undefined ? { code, message } : { code, message, minutes };
  }

  /**
   * Open-redirect guard for the {@code returnUrl} query
   * parameter. Returns the original value when it is a
   * same-origin relative path (starts with a single
   * {@code /}); returns {@code /dashboard} otherwise.
   */
  private validateReturnUrl(raw: string | null): string {
    if (raw === null || raw.length === 0) return '/dashboard';
    if (!raw.startsWith('/')) return '/dashboard';
    if (raw.startsWith('//')) return '/dashboard';
    return raw;
  }

  /**
   * Narrow the {@code ApiHttpError} to the envelope shape
   * ({@code error: { code, message, details }}) and return
   * the inner body. The interceptor normalises everything to
   * one of the two shapes declared in {@code ApiHttpError};
   * we type-narrow here so callers don't have to.
   */
  private readEnvelope(
    thrown: ApiHttpError,
  ): { code: string; message: string; details?: Record<string, string> } | null {
    const body = thrown.error;
    if (!body || typeof body !== 'object' || !('error' in body)) {
      return null;
    }
    const env = (body as { error?: unknown }).error;
    if (!env || typeof env !== 'object') return null;
    const e = env as { code?: unknown; message?: unknown; details?: unknown };
    if (typeof e.code !== 'string' || typeof e.message !== 'string') return null;
    const details =
      e.details && typeof e.details === 'object'
        ? (e.details as Record<string, string>)
        : undefined;
    return { code: e.code, message: e.message, details };
  }

  /**
   * Derive the lockout minutes from {@code details.retryAfterSeconds}
   * first, then from the {@code Retry-After} response header.
   * Returns {@code undefined} when neither is present (caller
   * renders the generic "en unos minutos" copy).
   */
  private lockoutMinutes(
    thrown: ApiHttpError,
    details?: Record<string, string>,
  ): number | undefined {
    const fromDetails = this.parseSeconds(details?.['retryAfterSeconds']);
    if (fromDetails !== undefined) {
      return Math.ceil(fromDetails / 60);
    }
    const headerValue = thrown.headers?.get('Retry-After');
    const fromHeader = this.parseSeconds(headerValue);
    if (fromHeader !== undefined) {
      return Math.ceil(fromHeader / 60);
    }
    return undefined;
  }

  /**
   * Parse a "seconds" value out of the candidate string.
   * The {@code Retry-After} HTTP header is allowed to be
   * either a delta-seconds integer or an HTTP-date; in v1
   * the backend only sends seconds, so we only accept
   * that format and return {@code undefined} for anything
   * else (an HTTP-date would be misinterpreted and is
   * rejected so the caller falls back to the generic
   * "en unos minutos" copy).
   */
  private parseSeconds(raw: string | null | undefined): number | undefined {
    if (raw === null || raw === undefined) return undefined;
    const trimmed = raw.trim();
    if (trimmed.length === 0) return undefined;
    if (!/^\d+$/.test(trimmed)) return undefined;
    const n = Number(trimmed);
    return Number.isFinite(n) && n >= 0 ? n : undefined;
  }
}
