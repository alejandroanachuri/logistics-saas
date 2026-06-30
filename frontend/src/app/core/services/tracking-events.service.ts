import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { RecordEventRequest, TrackingEvent } from '../types';

/**
 * TrackingEventsService — typed wrapper over the 2 endpoints
 * under {@code /api/v1/packages/{packageId}/events/**}.
 * Mirrors the {@code CompanyUsersService} pattern: thin
 * HttpClient calls, all error mapping delegated to the global
 * {@code errorInterceptor}.
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /} — record a new tracking event (drives
 *       the package FSM). ADMIN+OPERATOR.</li>
 *   <li>{@code GET  /} — full event history for the package
 *       (oldest-first). ADMIN+OPERATOR+VIEWER.</li>
 * </ul>
 *
 * <p>The URL path uses {@code /packages/{id}/events} where the
 * {@code id} may be either a real package id or a shipment id
 * — the backend's {@code TrackingEventService.record} resolves
 * the target via its own routing logic.
 */
@Injectable({ providedIn: 'root' })
export class TrackingEventsService {
  private readonly http = inject(HttpClient);

  /**
   * {@code POST /api/v1/packages/{packageId}/events}. Records a
   * new tracking event. Backend computes the {@code eventHash}
   * server-side for idempotency (clients MUST NOT supply it).
   * Returns the freshly-created event with the server-generated
   * id + createdAt.
   */
  record(packageId: string, req: RecordEventRequest): Observable<TrackingEvent> {
    return this.http.post<TrackingEvent>(`/api/v1/packages/${packageId}/events`, req);
  }

  /**
   * {@code GET /api/v1/packages/{packageId}/events}. Returns the
   * full event history for the package, oldest-first. The
   * timeline UI renders this directly.
   */
  list(packageId: string): Observable<TrackingEvent[]> {
    return this.http.get<TrackingEvent[]>(`/api/v1/packages/${packageId}/events`);
  }
}
