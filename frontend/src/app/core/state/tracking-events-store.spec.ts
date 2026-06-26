import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { TrackingEventsStore } from './tracking-events-store';
import { TrackingEventsService } from '../services/tracking-events.service';
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

describe('TrackingEventsStore', () => {
  let trackingEventsService: {
    list: ReturnType<typeof vi.fn>;
    record: ReturnType<typeof vi.fn>;
  };
  let store: TrackingEventsStore;

  beforeEach(() => {
    trackingEventsService = { list: vi.fn(), record: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: TrackingEventsService, useValue: trackingEventsService },
        TrackingEventsStore,
      ],
    });
    store = TestBed.inject(TrackingEventsStore);
  });

  describe('initial state', () => {
    it('starts empty: no events, no current shipment, not loading', () => {
      expect(store.events()).toEqual([]);
      expect(store.currentShipmentId()).toBeNull();
      expect(store.isLoading()).toBe(false);
      expect(store.isRecording()).toBe(false);
      expect(store.isTimelineEmpty()).toBe(true);
    });
  });

  describe('loadTimeline', () => {
    it('updates the events + currentShipmentId signals on success', async () => {
      const events: TrackingEvent[] = [makeEvent('e1', 'CREADO')];
      trackingEventsService.list.mockReturnValue(of(events));

      await store.loadTimeline('s1');

      expect(store.currentShipmentId()).toBe('s1');
      expect(store.events()).toEqual(events);
      expect(store.isLoading()).toBe(false);
      expect(store.isTimelineEmpty()).toBe(false);
    });

    it('leaves previous events intact on error and rethrows', async () => {
      trackingEventsService.list.mockReturnValueOnce(of([makeEvent('e1', 'CREADO')]));
      await store.loadTimeline('s1');
      expect(store.events().length).toBe(1);

      trackingEventsService.list.mockReturnValueOnce(throwError(() => new Error('boom')));
      await expect(store.loadTimeline('s2')).rejects.toThrow('boom');

      // Previous events preserved.
      expect(store.events().length).toBe(1);
      // currentShipmentId IS updated to the new id — failure on a
      // refresh still tells the UI "we tried to load this".
      expect(store.currentShipmentId()).toBe('s2');
    });
  });

  describe('record', () => {
    it('appends the new event to the local events signal and returns it', async () => {
      trackingEventsService.list.mockReturnValueOnce(of([makeEvent('e1', 'CREADO')]));
      await store.loadTimeline('s1');
      expect(store.events().length).toBe(1);

      const newEvent = makeEvent('e2', 'EN_TRANSITO_A_HUB');
      trackingEventsService.record.mockReturnValue(of(newEvent));

      const req: RecordEventRequest = { eventType: 'EN_TRANSITO_A_HUB', branchId: 'b1' };
      let result: TrackingEvent | undefined;
      await store.record('p1', req).then((r) => (result = r));

      expect(result).toEqual(newEvent);
      expect(store.events().length).toBe(2);
      expect(store.events()[1]).toEqual(newEvent);
      expect(store.isRecording()).toBe(false);
    });

    it('propagates errors and clears isRecording', async () => {
      trackingEventsService.record.mockReturnValueOnce(throwError(() => new Error('boom')));
      const req: RecordEventRequest = { eventType: 'CREADO' };
      await expect(store.record('p1', req)).rejects.toThrow('boom');
      expect(store.isRecording()).toBe(false);
    });
  });

  describe('clearTimeline', () => {
    it('resets the events + currentShipmentId signals', async () => {
      trackingEventsService.list.mockReturnValueOnce(of([makeEvent('e1', 'CREADO')]));
      await store.loadTimeline('s1');

      store.clearTimeline();

      expect(store.events()).toEqual([]);
      expect(store.currentShipmentId()).toBeNull();
    });
  });
});
