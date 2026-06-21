import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, ParamMap } from '@angular/router';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { LoginService } from './login.service';
import { AuthService } from './auth.service';
import { ApiHttpError } from '../types';
import { LoginRequest, LoginResponse } from '../types';

/**
 * Helper — build a minimal LoginResponse (AuthService.login
 * success) for the success-path tests.
 */
function makeLoginResponse(): LoginResponse {
  return {
    user: {
      id: 'user-1',
      tenantId: 'tenant-1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: 'juan@mvr.test',
      firstName: 'Juan',
      lastName: 'Perez',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: true,
    },
    expiresIn: 900,
  };
}

/**
 * Helper — build an {@code ApiHttpError} shaped like the
 * post-interceptor payload (envelope under {@code error.error}).
 */
function makeApiHttpError(
  status: number,
  body: ApiHttpError['error'],
  headers?: Record<string, string>,
): ApiHttpError {
  const err = new Error(`HTTP ${status}`) as unknown as ApiHttpError;
  (err as unknown as { status: number }).status = status;
  (err as unknown as { statusText: string }).statusText = 'Error';
  (err as unknown as { error: ApiHttpError['error'] }).error = body;
  if (headers) {
    (err as unknown as { headers: { get: (k: string) => string | null } }).headers = {
      get: (k: string) => (k in headers ? headers[k] : null),
    };
  }
  return err;
}

describe('LoginService', () => {
  let authService: { login: ReturnType<typeof vi.fn> };
  let service: LoginService;

  function configureRoute(queryParamMap: ParamMap): void {
    TestBed.configureTestingModule({
      providers: [
        LoginService,
        {
          provide: AuthService,
          useValue: authService,
        },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap },
          },
        },
      ],
    });
    service = TestBed.inject(LoginService);
  }

  beforeEach(() => {
    authService = { login: vi.fn() };
  });

  describe('submit', () => {
    it('delegates to AuthService.login(creds) and returns the resulting observable', () => {
      configureRoute(convertToParamMap({}));
      const creds: LoginRequest = { slug: 'mvr', username: 'juan', password: 'Secret123!' };
      const resp = makeLoginResponse();
      authService.login.mockReturnValue(of(resp));

      let emitted: LoginResponse | undefined;
      service.submit(creds).subscribe((r) => (emitted = r));

      expect(authService.login).toHaveBeenCalledTimes(1);
      expect(authService.login).toHaveBeenCalledWith(creds);
      expect(emitted).toEqual(resp);
    });

    it('does not implicitly navigate — the caller drives navigation from the subscribe', () => {
      // The service has no router reference at all. It just
      // returns the AuthService observable. The component
      // subscribes, sees the LoginResponse, and calls
      // router.navigate() itself. This test documents that
      // contract: a second .subscribe() should not produce a
      // second effect on any global state.
      configureRoute(convertToParamMap({}));
      const creds: LoginRequest = { slug: 'mvr', username: 'juan', password: 'Secret123!' };
      const resp = makeLoginResponse();
      authService.login.mockReturnValue(of(resp));

      let count = 0;
      service.submit(creds).subscribe(() => count++);
      service.submit(creds).subscribe(() => count++);

      expect(count).toBe(2);
      expect(authService.login).toHaveBeenCalledTimes(2);
    });
  });

  describe('extractErrorCopy', () => {
    beforeEach(() => {
      configureRoute(convertToParamMap({}));
    });

    it('returns the envelope message for INVALID_CREDENTIALS', () => {
      const thrown = makeApiHttpError(401, {
        error: { code: 'INVALID_CREDENTIALS', message: 'bad' },
      });

      const copy = service.extractErrorCopy(thrown);

      expect(copy).toEqual({ code: 'INVALID_CREDENTIALS', message: 'bad' });
    });

    it('derives minutes from details.retryAfterSeconds for ACCOUNT_LOCKED', () => {
      // 90s → ceil(90/60) = 2 minutes
      const thrown = makeApiHttpError(403, {
        error: {
          code: 'ACCOUNT_LOCKED',
          message: 'locked',
          details: { retryAfterSeconds: '90' },
        },
      });

      const copy = service.extractErrorCopy(thrown);

      expect(copy).toEqual({ code: 'ACCOUNT_LOCKED', message: 'locked', minutes: 2 });
    });

    it('falls back to the Retry-After header when ACCOUNT_LOCKED has no details', () => {
      // 120s → 2 minutes, derived from the header
      const thrown = makeApiHttpError(
        403,
        { error: { code: 'ACCOUNT_LOCKED', message: 'locked' } },
        { 'Retry-After': '120' },
      );

      const copy = service.extractErrorCopy(thrown);

      expect(copy).toEqual({ code: 'ACCOUNT_LOCKED', message: 'locked', minutes: 2 });
    });

    it('omits the minutes field when ACCOUNT_LOCKED has no details and no Retry-After header', () => {
      const thrown = makeApiHttpError(403, {
        error: { code: 'ACCOUNT_LOCKED', message: 'locked' },
      });

      const copy = service.extractErrorCopy(thrown);

      expect(copy).toEqual({ code: 'ACCOUNT_LOCKED', message: 'locked' });
      expect('minutes' in copy).toBe(false);
    });

    it('returns the envelope message for unknown codes (Spanish fallback from envelope)', () => {
      const thrown = makeApiHttpError(500, {
        error: { code: 'INTERNAL_ERROR', message: 'Error interno' },
      });

      const copy = service.extractErrorCopy(thrown);

      expect(copy).toEqual({ code: 'INTERNAL_ERROR', message: 'Error interno' });
    });
  });

  describe('returnUrl validation (read at construction)', () => {
    it('exposes a relative returnUrl as-is', () => {
      configureRoute(convertToParamMap({ returnUrl: '/dashboard/profile' }));
      expect(service.safeReturnUrl()).toBe('/dashboard/profile');
    });

    it('falls back to /dashboard when returnUrl is missing', () => {
      configureRoute(convertToParamMap({}));
      expect(service.safeReturnUrl()).toBe('/dashboard');
    });

    it.each([
      ['//evil.com/path', 'protocol-relative URL open-redirect'],
      ['https://evil.com/path', 'absolute-URL open-redirect'],
    ])('rejects %s (%s) and falls back to /dashboard', (bad, _label) => {
      configureRoute(convertToParamMap({ returnUrl: bad }));
      expect(service.safeReturnUrl()).toBe('/dashboard');
    });
  });
});
