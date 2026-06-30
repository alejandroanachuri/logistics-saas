import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { PublicTrackService } from './public-track.service';
import { PublicTrackResponse } from '../types';

function makeResponse(trackingId: string): PublicTrackResponse {
  return {
    trackingId,
    status: 'EN_TRANSITO',
    statusMessage: 'En tránsito a destino',
    isPartial: false,
    packageCount: 1,
    totalWeightKg: 1.5,
    receiverName: 'Juan P.',
    timeline: [
      { timestamp: '2026-01-01T10:00:00Z', message: 'Recibido en sucursal de origen' },
      { timestamp: '2026-01-02T15:30:00Z', message: 'En tránsito a destino' },
    ],
  };
}

describe('PublicTrackService', () => {
  let httpMock: { get: ReturnType<typeof vi.fn> };
  let service: PublicTrackService;

  beforeEach(() => {
    httpMock = { get: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, PublicTrackService],
    });
    service = TestBed.inject(PublicTrackService);
  });

  describe('track', () => {
    it('GETs /api/v1/public/track/{trackingId} and returns the public-safe response', () => {
      const resp = makeResponse('LGST-A3K9P2RX');
      httpMock.get.mockReturnValue(of(resp));

      let emitted: PublicTrackResponse | undefined;
      service.track('LGST-A3K9P2RX').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/public/track/LGST-A3K9P2RX');
      expect(emitted).toEqual(resp);
    });

    it('URI-encodes tracking ids that contain reserved characters', () => {
      const resp = makeResponse('LGST/A3K9');
      httpMock.get.mockReturnValue(of(resp));

      service.track('LGST/A3K9').subscribe();

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/public/track/LGST%2FA3K9');
    });
  });
});
