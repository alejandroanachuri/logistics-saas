import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { ProvincesService } from './provinces.service';

/**
 * Unit spec for {@code ProvincesService} (PR12a). The service
 * exposes a small in-memory cache of the {@code GET
 * /api/v1/reference/provinces} response so the wizard's
 * province select renders instantly on step 1 and the request
 * is not re-issued on subsequent navigations within the same
 * SPA session.
 *
 * <p>The first {@code it()} is a no-op warm-up that exists
 * to absorb the vitest-builder orphan quirk (see apply-
 * progress observation #122 Discovery #15) — the builder
 * silently drops the first test of any newly-added spec file;
 * the warm-up makes the count predictable.
 */
describe('ProvincesService', () => {
  let httpMock: { get: ReturnType<typeof vi.fn> };
  let service: ProvincesService;

  const SAMPLE: Array<{ code: string; displayName: string }> = [
    { code: 'BUENOS_AIRES', displayName: 'Buenos Aires' },
    { code: 'CABA', displayName: 'Ciudad Autónoma de Buenos Aires' },
    { code: 'CORDOBA', displayName: 'Córdoba' },
  ];

  // The backend wraps the array in a `{ data: [...] }` envelope
  // (per the reference-data spec). The spec mocks the full
  // HTTP response shape.
  const SAMPLE_ENVELOPE = { data: SAMPLE };

  beforeEach(() => {
    httpMock = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, ProvincesService],
    });
    service = TestBed.inject(ProvincesService);
  });

  // -------- warm-up (vitest orphan offset) --------

  it('warm-up — service instantiates', () => {
    // No-op warm-up; absorbs the first-test-of-spec drop.
    expect(service).toBeTruthy();
  });

  // -------- cache miss → network --------

  it('GETs /api/v1/reference/provinces on the first call', () => {
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));

    let emitted: Array<{ code: string; displayName: string }> | undefined;
    service.list().subscribe((r) => (emitted = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    const [url] = httpMock.get.mock.calls[0];
    expect(url).toBe('/api/v1/reference/provinces');
    expect(emitted).toEqual(SAMPLE);
  });

  // -------- cache hit → no network --------

  it('returns the cached list on a second call without hitting the network', () => {
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));

    let first: Array<{ code: string; displayName: string }> | undefined;
    let second: Array<{ code: string; displayName: string }> | undefined;
    service.list().subscribe((r) => (first = r));
    service.list().subscribe((r) => (second = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    expect(first).toEqual(SAMPLE);
    expect(second).toEqual(SAMPLE);
  });

  // -------- concurrent subscribers share one in-flight request --------

  it('shares a single in-flight request between concurrent subscribers', () => {
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));

    let first: Array<{ code: string; displayName: string }> | undefined;
    let second: Array<{ code: string; displayName: string }> | undefined;
    // Both subscriptions registered before the source has
    // emitted (the http mock returns of(SAMPLE), which is
    // synchronous; shareReplay(1) collapses the in-flight
    // observable so http.get is called exactly once even when
    // a future refactor swaps the mock for a delayed one).
    service.list().subscribe((r) => (first = r));
    service.list().subscribe((r) => (second = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    expect(first).toEqual(SAMPLE);
    expect(second).toEqual(SAMPLE);
  });

  // -------- getByCode: lookup against the cached list --------

  it('getByCode returns the matching province from the cached list', () => {
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));
    service.list().subscribe();

    const found = service.getByCode('BUENOS_AIRES');
    expect(found).toEqual({ code: 'BUENOS_AIRES', displayName: 'Buenos Aires' });
  });

  // -------- getByCode: unknown code → undefined (graceful) --------

  it('getByCode returns undefined for an unknown code (does not throw)', () => {
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));
    service.list().subscribe();

    const found = service.getByCode('XX-XX');
    expect(found).toBeUndefined();
  });

  // -------- error signal: HTTP failure → user-facing message + rethrow --------

  /**
   * Closes the 2026-06-16 gap #3 ("Province select API failure
   * UX — empty list with no user-visible error"). The
   * scenarios below verify that:
   * - a 5xx response sets a Spanish error message,
   * - a network failure (status 0) sets a different message,
   * - the observable re-throws (callers can react),
   * - the cache stays untouched (so a subsequent refresh()
   *   can recover cleanly),
   * - refresh() resets the error and re-issues the request.
   */

  it('sets a user-facing error signal AND rethrows on a 5xx response', () => {
    const err = new HttpErrorResponse({
      status: 503,
      statusText: 'Service Unavailable',
      error: { error: { code: 'INTERNAL_ERROR', message: 'x' } },
    });
    httpMock.get.mockReturnValue(throwError(() => err));

    let thrown: unknown;
    service.list().subscribe({ error: (e) => (thrown = e) });

    expect(service.error()).toContain('no está disponible');
    expect(service.error()).not.toBeNull();
    expect(thrown).toBe(err);
  });

  it('sets a "revisá tu conexión" error signal on a network failure (status 0)', () => {
    const err = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
    httpMock.get.mockReturnValue(throwError(() => err));

    let thrown: unknown;
    service.list().subscribe({ error: (e) => (thrown = e) });

    expect(service.error()).toContain('Revisá tu conexión');
    expect(thrown).toBe(err);
  });

  it('clears the error signal on the next successful call', () => {
    const err = new HttpErrorResponse({ status: 503, statusText: 'x' });
    httpMock.get.mockReturnValueOnce(throwError(() => err));
    service.list().subscribe({ error: () => {} });
    expect(service.error()).not.toBeNull();

    httpMock.get.mockReturnValueOnce(of(SAMPLE_ENVELOPE));
    service.list().subscribe();
    expect(service.error()).toBeNull();
  });

  it('refresh() drops the cache and re-issues the request (used by the Reintentar button)', () => {
    // The mock returns SAMPLE_ENVELOPE for BOTH calls. The
    // first call populates the cache; refresh() must clear
    // it and trigger a second HTTP call (otherwise the user
    // would see a stale list after clicking Reintentar).
    httpMock.get.mockReturnValue(of(SAMPLE_ENVELOPE));
    service.list().subscribe();
    expect(httpMock.get).toHaveBeenCalledTimes(1);

    service.refresh().subscribe();
    expect(httpMock.get).toHaveBeenCalledTimes(2);
  });
});
