import { TestBed } from '@angular/core/testing';
import { HttpHandlerFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { authInterceptor } from './auth-interceptor';

/**
 * Tiny HttpHandler that captures the request the chain
 * actually received, so the test can assert the interceptor
 * mutated it before it hit the next handler in the chain.
 */
function makeHandler() {
  const handle = vi.fn((req: HttpRequest<unknown>) => of(new HttpResponse({ status: 200, url: req.url })));
  const handler: HttpHandlerFn = (req) => handle(req);
  return { handle, handler };
}

describe('authInterceptor', () => {
  it('clones the request with withCredentials = true and passes the body through', async () => {
    const { handle, handler } = makeHandler();
    const req = new HttpRequest('POST', '/api/v1/auth/login', { slug: 'mvr' });
    let received: HttpRequest<unknown> | undefined;
    handle.mockImplementation((r) => {
      received = r;
      return of(new HttpResponse({ status: 200 }));
    });

    TestBed.configureTestingModule({ providers: [] });
    await TestBed.runInInjectionContext(() => new Promise<void>((resolve) => {
      authInterceptor(req, handler).subscribe(() => {
        expect(received).toBeDefined();
        expect(received!.withCredentials).toBe(true);
        // Body payload is preserved across the clone.
        expect(received!.body).toEqual({ slug: 'mvr' });
        expect(received!.method).toBe('POST');
        expect(received!.url).toBe('/api/v1/auth/login');
        // Cloned request is a different object reference.
        expect(received).not.toBe(req);
        resolve();
      });
    }));
  });

  it('attaches withCredentials to a plain GET (no body) too', async () => {
    const { handle, handler } = makeHandler();
    const req = new HttpRequest('GET', '/api/v1/auth/me');
    let received: HttpRequest<unknown> | undefined;
    handle.mockImplementation((r) => {
      received = r;
      return of(new HttpResponse({ status: 200 }));
    });

    await TestBed.runInInjectionContext(() => new Promise<void>((resolve) => {
      authInterceptor(req, handler).subscribe(() => {
        expect(received).toBeDefined();
        expect(received!.withCredentials).toBe(true);
        expect(received!.url).toBe('/api/v1/auth/me');
        resolve();
      });
    }));
  });
});
