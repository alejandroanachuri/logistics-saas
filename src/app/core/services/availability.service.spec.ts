import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { AvailabilityService } from './availability.service';
import { AvailabilityResponse } from '../types';

describe('AvailabilityService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
  };
  let service: AvailabilityService;

  beforeEach(() => {
    httpMock = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, AvailabilityService],
    });
    service = TestBed.inject(AvailabilityService);
  });

  describe('checkSlug', () => {
    it('GETs /api/v1/tenants/me/slug-availability with the slug query param', () => {
      const resp: AvailabilityResponse = { available: true };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkSlug('mvr').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledTimes(1);
      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/tenants/me/slug-availability');
      // options is { params: HttpParams }
      const params: HttpParams = options.params;
      expect(params.get('slug')).toBe('mvr');
      expect(emitted).toEqual(resp);
    });

    it('returns the reason when the slug is taken', () => {
      const resp: AvailabilityResponse = { available: false, reason: 'SLUG_ALREADY_TAKEN' };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkSlug('taken').subscribe((r) => (emitted = r));

      expect(emitted).toEqual(resp);
    });
  });

  describe('checkCuit', () => {
    it('GETs /api/v1/tenants/me/cuit-availability with the cuit query param', () => {
      const resp: AvailabilityResponse = { available: true };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkCuit('30712345678').subscribe((r) => (emitted = r));

      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/tenants/me/cuit-availability');
      const params: HttpParams = options.params;
      expect(params.get('cuit')).toBe('30712345678');
      expect(emitted).toEqual(resp);
    });

    it('returns the reason when the cuit is registered', () => {
      const resp: AvailabilityResponse = { available: false, reason: 'CUIT_ALREADY_REGISTERED' };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkCuit('30712345678').subscribe((r) => (emitted = r));

      expect(emitted).toEqual(resp);
    });
  });

  describe('checkUsername', () => {
    it('GETs /api/v1/tenants/me/username-availability with both slug and username', () => {
      const resp: AvailabilityResponse = { available: true };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkUsername('mvr', 'juan').subscribe((r) => (emitted = r));

      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/tenants/me/username-availability');
      const params: HttpParams = options.params;
      expect(params.get('slug')).toBe('mvr');
      expect(params.get('username')).toBe('juan');
      expect(emitted).toEqual(resp);
    });

    it('returns the reason when the username is taken', () => {
      const resp: AvailabilityResponse = { available: false, reason: 'USERNAME_ALREADY_TAKEN' };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: AvailabilityResponse | undefined;
      service.checkUsername('mvr', 'admin').subscribe((r) => (emitted = r));

      expect(emitted).toEqual(resp);
    });
  });
});
