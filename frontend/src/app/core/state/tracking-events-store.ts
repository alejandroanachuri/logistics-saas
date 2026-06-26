import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { TrackingEventsService } from '../services/tracking-events.service';
import { RecordEventRequest, TrackingEvent } from '../types';

/**
 * TrackingEventsStore — signal-based state for the timeline +
 * operator-event-recording flows (etapa-3-envios PR-5).
 * Mirrors the {@code CompanyUsersStore} pattern: signals for
 * derived reads, methods for actions.
 *
 * <p>The store holds the timeline events for a single shipment
 * (typically fetched lazily when the shipment-detail page
 * mounts the timeline tab) plus a transient "isRecording"
 * flag used to disable the event-recording form while the
 * POST is in flight.
 *
 * <p>Error policy: on failure, the events signal keeps its
 * previous value (so the UI does not flash empty during an
 * intermittent failure) and the error is rethrown to the
 * caller. Callers catch and surface the localized copy from
 * the {@code errorInterceptor}.
 */
@Injectable({ providedIn: 'root' })
export class TrackingEventsStore {
  private readonly trackingEventsService = inject(TrackingEventsService);

  private readonly _events = signal<TrackingEvent[]>([]);
  private readonly _currentShipmentId = signal<string | null>(null);
  private readonly _isLoading = signal<boolean>(false);
  private readonly _isRecording = signal<boolean>(false);

  readonly events = this._events.asReadonly();
  readonly currentShipmentId = this._currentShipmentId.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isRecording = this._isRecording.asReadonly();

  /** Convenience flag: the store has loaded events for a
   * shipment and the timeline is currently empty. The
   * timeline UI uses this to decide between the event list
   * and the empty-state card. */
  readonly isTimelineEmpty = computed(() => !this._isLoading() && this._events().length === 0);

  /** Fetch the event history for a shipment (or package — the
   * URL path is the same). Updates the events + currentShipmentId
   * signals on success. */
  async loadTimeline(shipmentOrPackageId: string): Promise<void> {
    this._currentShipmentId.set(shipmentOrPackageId);
    this._isLoading.set(true);
    try {
      const events = await firstValueFrom(this.trackingEventsService.list(shipmentOrPackageId));
      this._events.set(events);
    } finally {
      this._isLoading.set(false);
    }
  }

  /** Record a new tracking event. On success the event is
   * appended to the local events signal so the UI updates
   * without a refetch, and the freshly-created event is
   * returned. */
  async record(packageId: string, req: RecordEventRequest): Promise<TrackingEvent> {
    this._isRecording.set(true);
    try {
      const event = await firstValueFrom(this.trackingEventsService.record(packageId, req));
      this._events.update((current) => [...current, event]);
      return event;
    } finally {
      this._isRecording.set(false);
    }
  }

  /** Clear the cached events (used by the shipment-detail /
   * timeline pages when navigating away). */
  clearTimeline(): void {
    this._events.set([]);
    this._currentShipmentId.set(null);
  }
}
