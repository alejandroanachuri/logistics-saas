import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  CancelShipmentRequest,
  CreateShipmentRequest,
  CreateShipmentResponse,
  PageResponse,
  RejectShipmentRequest,
  ShipmentDetail,
  ShipmentListFilters,
  ShipmentSummary,
  TrackingEvent,
  UpdateShipmentRequest,
} from '../types';

/**
 * ShipmentsService — typed wrapper over the 8 endpoints under
 * {@code /api/v1/shipments/**}. Mirrors the
 * {@code CompanyUsersService} pattern: thin HttpClient calls,
 * all error mapping delegated to the global
 * {@code errorInterceptor}.
 *
 * <p>Eight endpoints:
 * <ul>
 *   <li>{@code POST  /}                  — create (ADMIN+OPERATOR)</li>
 *   <li>{@code GET   /}                  — list paginated (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code GET   /{id}}              — detail (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code GET   /{id}/timeline}     — event history (ADMIN+OPERATOR+VIEWER)</li>
 *   <li>{@code PATCH /{id}}              — partial update, only PRE_ALTA (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/validate}     — PRE_ALTA → CREADO (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/reject}       — PRE_ALTA → CANCELADO (ADMIN+OPERATOR)</li>
 *   <li>{@code POST  /{id}/cancel}       — any → CANCELADO (ADMIN only)</li>
 * </ul>
 *
 * <p>The service also exposes package-level event recording via
 * {@link TrackingEventsService} (separate, mounted under
 * {@code /api/v1/packages/{id}/events/**}).
 */
@Injectable({ providedIn: 'root' })
export class ShipmentsService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/shipments';

  /**
   * {@code GET /api/v1/shipments?page=...&size=...&sort=...&status=...&dateFrom=...&dateTo=...&search=...}.
   * Returns the paged envelope with sender + receiver display names
   * already enriched by the controller.
   */
  list(filters: ShipmentListFilters): Observable<PageResponse<ShipmentSummary>> {
    let httpParams = new HttpParams();
    if (filters.page !== undefined) httpParams = httpParams.set('page', String(filters.page));
    if (filters.size !== undefined) httpParams = httpParams.set('size', String(filters.size));
    if (filters.sort !== undefined) httpParams = httpParams.set('sort', filters.sort);
    if (filters.status !== undefined) httpParams = httpParams.set('status', filters.status);
    if (filters.dateFrom !== undefined) httpParams = httpParams.set('dateFrom', filters.dateFrom);
    if (filters.dateTo !== undefined) httpParams = httpParams.set('dateTo', filters.dateTo);
    if (filters.search !== undefined) httpParams = httpParams.set('search', filters.search);

    return this.http.get<PageResponse<ShipmentSummary>>(ShipmentsService.BASE, {
      params: httpParams,
    });
  }

  /** {@code GET /api/v1/shipments/{id}}. Returns the full detail
   * with FK enrichments + packages + latestEvent. */
  get(id: string): Observable<ShipmentDetail> {
    return this.http.get<ShipmentDetail>(`${ShipmentsService.BASE}/${id}`);
  }

  /** {@code GET /api/v1/shipments/{id}/timeline}. Returns the
   * full event history for the shipment (events across all
   * packages flattened, oldest-first). */
  getTimeline(id: string): Observable<TrackingEvent[]> {
    return this.http.get<TrackingEvent[]>(`${ShipmentsService.BASE}/${id}/timeline`);
  }

  /** {@code POST /api/v1/shipments}. Returns the create envelope
   * (summary + packages). When {@code validateNow} is true the
   * backend runs the FSM validate pass inline. */
  create(req: CreateShipmentRequest): Observable<CreateShipmentResponse> {
    return this.http.post<CreateShipmentResponse>(ShipmentsService.BASE, req);
  }

  /** {@code PATCH /api/v1/shipments/{id}}. Only allowed on
   * PRE_ALTA shipments. Returns the refreshed detail so the
   * caller does not need a follow-up GET. */
  update(id: string, req: UpdateShipmentRequest): Observable<ShipmentDetail> {
    return this.http.patch<ShipmentDetail>(`${ShipmentsService.BASE}/${id}`, req);
  }

  /** {@code POST /api/v1/shipments/{id}/validate}. PRE_ALTA →
   * CREADO transition. Returns the refreshed detail. */
  validate(id: string): Observable<ShipmentDetail> {
    return this.http.post<ShipmentDetail>(`${ShipmentsService.BASE}/${id}/validate`, null);
  }

  /** {@code POST /api/v1/shipments/{id}/reject}. PRE_ALTA →
   * CANCELADO transition with a mandatory {@code rejectionReason}.
   * Returns the refreshed detail. */
  reject(id: string, reason: string): Observable<ShipmentDetail> {
    const body: RejectShipmentRequest = { rejectionReason: reason };
    return this.http.post<ShipmentDetail>(`${ShipmentsService.BASE}/${id}/reject`, body);
  }

  /** {@code POST /api/v1/shipments/{id}/cancel}. Any non-final
   * state → CANCELADO. ADMIN only. Mandatory {@code reason}.
   * Returns the refreshed detail. */
  cancel(id: string, reason: string): Observable<ShipmentDetail> {
    const body: CancelShipmentRequest = { reason };
    return this.http.post<ShipmentDetail>(`${ShipmentsService.BASE}/${id}/cancel`, body);
  }
}
