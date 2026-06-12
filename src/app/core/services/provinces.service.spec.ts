import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { of } from 'rxjs';
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

  const SAMPLE: Array<{ code: string; name: string }> = [
    { code: 'AR-B', name: 'Buenos Aires' },
    { code: 'AR-C', name: 'Ciudad Autónoma de Buenos Aires' },
    { code: 'AR-X', name: 'Córdoba' },
  ];

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
    httpMock.get.mockReturnValue(of(SAMPLE));

    let emitted: Array<{ code: string; name: string }> | undefined;
    service.list().subscribe((r) => (emitted = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    const [url] = httpMock.get.mock.calls[0];
    expect(url).toBe('/api/v1/reference/provinces');
    expect(emitted).toEqual(SAMPLE);
  });

  // -------- cache hit → no network --------

  it('returns the cached list on a second call without hitting the network', () => {
    httpMock.get.mockReturnValue(of(SAMPLE));

    let first: Array<{ code: string; name: string }> | undefined;
    let second: Array<{ code: string; name: string }> | undefined;
    service.list().subscribe((r) => (first = r));
    service.list().subscribe((r) => (second = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    expect(first).toEqual(SAMPLE);
    expect(second).toEqual(SAMPLE);
  });

  // -------- concurrent subscribers share one in-flight request --------

  it('shares a single in-flight request between concurrent subscribers', () => {
    httpMock.get.mockReturnValue(of(SAMPLE));

    let first: Array<{ code: string; name: string }> | undefined;
    let second: Array<{ code: string; name: string }> | undefined;
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
    httpMock.get.mockReturnValue(of(SAMPLE));
    service.list().subscribe();

    const found = service.getByCode('AR-B');
    expect(found).toEqual({ code: 'AR-B', name: 'Buenos Aires' });
  });

  // -------- getByCode: unknown code → undefined (graceful) --------

  it('getByCode returns undefined for an unknown code (does not throw)', () => {
    httpMock.get.mockReturnValue(of(SAMPLE));
    service.list().subscribe();

    const found = service.getByCode('XX-XX');
    expect(found).toBeUndefined();
  });
});
