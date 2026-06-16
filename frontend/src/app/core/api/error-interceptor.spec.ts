import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHandlerFn, HttpHeaders, HttpRequest, HttpResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { errorInterceptor } from './error-interceptor';
import { AuthStore } from '../state/auth-store';

/**
 * Tiny HttpHandler that either returns ok or throws the
 * supplied {@code HttpErrorResponse} on the first call.
 */
function makeHandler(err?: HttpErrorResponse) {
  const handle = vi.fn((req: HttpRequest<unknown>) => {
    if (err) {
      return throwError(() => err);
    }
    return of(new HttpResponse({ status: 200, url: req.url }));
  });
  const handler: HttpHandlerFn = (req) => handle(req);
  return { handle, handler };
}

describe('errorInterceptor', () => {
  describe('envelope mapping', () => {
    it('passes through a 2xx response unchanged', async () => {
      const { handler } = makeHandler();
      const req = new HttpRequest('GET', '/api/v1/tenants/me');

      const result = await new Promise<unknown>((resolve, reject) => {
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            next: (v) => resolve(v),
            error: (e) => reject(e),
          });
        });
      });

      expect(result).toBeDefined();
    });

    it('normalises status 0 to a NETWORK_ERROR envelope', async () => {
      const err = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
      const { handler } = makeHandler(err);
      const req = new HttpRequest('GET', '/api/v1/tenants/me');

      const thrown = await new Promise<HttpErrorResponse>((resolve) => {
        TestBed.configureTestingModule({ providers: [AuthStore] });
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({ error: (e) => resolve(e as HttpErrorResponse) });
        });
      });

      const env = (thrown.error as { error: { code: string; message: string } }).error;
      expect(env.code).toBe('NETWORK_ERROR');
      expect(env.message).toContain('conectarnos');
    });

    it('re-throws a 401 with the INVALID_CREDENTIALS code from the envelope', async () => {
      const err = new HttpErrorResponse({
        status: 401,
        statusText: 'Unauthorized',
        error: { error: { code: 'INVALID_CREDENTIALS', message: 'bad' } },
      });
      const { handler } = makeHandler(err);
      const req = new HttpRequest('POST', '/api/v1/auth/login', { slug: 'mvr' });

      const thrown = await new Promise<HttpErrorResponse>((resolve) => {
        TestBed.configureTestingModule({ providers: [AuthStore] });
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            error: (e) => resolve(e as HttpErrorResponse),
          });
        });
      });

      const env = (thrown.error as { error: { code: string } }).error;
      expect(env.code).toBe('INVALID_CREDENTIALS');
      expect(thrown.status).toBe(401);
    });

    it('preserves the 400 details object on a VALIDATION_ERROR', async () => {
      const err = new HttpErrorResponse({
        status: 400,
        statusText: 'Bad Request',
        error: { error: { code: 'VALIDATION_ERROR', message: 'bad', details: { slug: 'required' } } },
      });
      const { handler } = makeHandler(err);
      const req = new HttpRequest('POST', '/api/v1/auth/register', {});

      const thrown = await new Promise<HttpErrorResponse>((resolve) => {
        TestBed.configureTestingModule({ providers: [AuthStore] });
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            error: (e) => resolve(e as HttpErrorResponse),
          });
        });
      });

      const env = (thrown.error as { error: { code: string; details: Record<string, string> } }).error;
      expect(env.code).toBe('VALIDATION_ERROR');
      expect(env.details).toEqual({ slug: 'required' });
    });
  });

  describe('response header projection', () => {
    /**
     * Warm-up scenario for the vitest orphan quirk (PR11c
     * discovery #15): the first {@code it} of a describe block
     * is silently dropped by vitest 4 + @angular/build:unit-test.
     * The 5 substantive scenarios below produce 4 reported
     * tests; the 2 we add here produce 1 additional reported
     * test (plus the 2 from the previous describe blocks).
     */
    it('warm-up: passes through a 2xx response unchanged', async () => {
      const { handler } = makeHandler();
      const req = new HttpRequest('GET', '/api/v1/tenants/me');

      const result = await new Promise<unknown>((resolve, reject) => {
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            next: (v) => resolve(v),
            error: (e) => reject(e),
          });
        });
      });

      expect(result).toBeDefined();
    });

    it('projects the original response headers onto the rethrown error', async () => {
      const err = new HttpErrorResponse({
        status: 401,
        statusText: 'Unauthorized',
        headers: new HttpHeaders({ 'Retry-After': '120' }),
        error: { error: { code: 'ACCOUNT_LOCKED', message: 'locked' } },
      });
      const { handler } = makeHandler(err);
      const req = new HttpRequest('POST', '/api/v1/auth/login', { slug: 'mvr' });

      const thrown = await new Promise<HttpErrorResponse>((resolve) => {
        TestBed.configureTestingModule({ providers: [AuthStore] });
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            error: (e) => resolve(e as HttpErrorResponse),
          });
        });
      });

      // The interceptor projects the original HttpErrorResponse's
      // headers into the rethrown object alongside the parsed
      // envelope at `thrown.error`. The shape of the rethrown
      // object is { error, headers, status, ... } where headers
      // comes from the original response.
      const apiErr = thrown as { error: unknown; headers: { get(name: string): string | null } };
      expect(apiErr.headers.get('Retry-After')).toBe('120');
    });

    it('projects an empty headers object when the response has no headers (network failure)', async () => {
      const err = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
      const { handler } = makeHandler(err);
      const req = new HttpRequest('GET', '/api/v1/tenants/me');

      const thrown = await new Promise<HttpErrorResponse>((resolve) => {
        TestBed.configureTestingModule({ providers: [AuthStore] });
        TestBed.runInInjectionContext(() => {
          errorInterceptor(req, handler).subscribe({
            error: (e) => resolve(e as HttpErrorResponse),
          });
        });
      });

      // Even on a network failure the headers object is
      // present (empty), so call sites can do `err.headers.get(k)`
      // without null-checking.
      const apiErr = thrown as { error: unknown; headers: { get(name: string): string | null } };
      expect(apiErr.headers).toBeDefined();
      expect(apiErr.headers.get('Retry-After')).toBeNull();
    });
  });

  describe('no-retry paths', () => {
    it('does NOT call fetch when a 401 happens on /api/v1/auth/login', async () => {
      const fetchSpy = vi.fn(() =>
        Promise.resolve(new Response('', { status: 200 })),
      );
      const originalFetch = globalThis.fetch;
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (globalThis as any).fetch = fetchSpy;

      try {
        const err = new HttpErrorResponse({
          status: 401,
          statusText: 'Unauthorized',
          error: { error: { code: 'INVALID_CREDENTIALS', message: 'bad' } },
        });
        const { handler } = makeHandler(err);
        const req = new HttpRequest('POST', '/api/v1/auth/login', { slug: 'mvr' });

        await new Promise<void>((resolve) => {
          TestBed.configureTestingModule({ providers: [AuthStore] });
          TestBed.runInInjectionContext(() => {
            errorInterceptor(req, handler).subscribe({
              error: () => resolve(),
            });
          });
        });

        expect(fetchSpy).not.toHaveBeenCalled();
      } finally {
        globalThis.fetch = originalFetch;
      }
    });
  });
});
