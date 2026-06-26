import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { TrackingEventsService } from './tracking-events.service';
import { RecordEventRequest, TrackingEvent } from '../types';

function makeEvent(id: string, eventType: string): TrackingEvent {
  return {
    id,
    packageId: 'p1',
    eventType,
    eventTimestamp: '2026-01-01T00:00:00Z',
    branchId: 'b1',
    userId: 'u1',
    eventSource: 'OPERADOR_SUCURSAL',
    metadata: null,
    createdAt: '2026-01-01T00:00:00Z',
  };
}

describe('TrackingEventsService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
  };
  let service: TrackingEventsService;

  beforeEach(() => {
    httpMock = { get: vi.fn(), post: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, TrackingEventsService],
    });
    service = TestBed.inject(TrackingEventsService);
  });

  describe('record', () => {
    it('POSTs to /api/v1/packages/{packageId}/events and returns the created event', () => {
      const req: RecordEventRequest = { eventType: 'CREADO', branchId: 'b1' };
      const event = makeEvent('e-new', 'CREADO');
      httpMock.post.mockReturnValue(of(event));

      let emitted: TrackingEvent | undefined;
      service.record('p1', req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/packages/p1/events', req);
      expect(emitted).toEqual(event);
    });
  });

  describe('list', () => {
    it('GETs /api/v1/packages/{packageId}/events and returns the events list', () => {
      const events: TrackingEvent[] = [makeEvent('e1', 'CREADO')];
      httpMock.get.mockReturnValue(of(events));

      let emitted: TrackingEvent[] | undefined;
      service.list('p1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/packages/p1/events');
      expect(emitted).toEqual(events);
    });
  });
});
