import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { PublicTrackResponse } from '../types';

/**
 * PublicTrackService — typed wrapper over the public tracking
 * portal endpoint
 * {@code GET /api/v1/public/track/{trackingId}}. This is the
 * ONLY endpoint on the company-side origin that is NOT behind
 * the auth filter.
 *
 * <p>The {@code authInterceptor} still attaches
 * {@code withCredentials: true} to the request — that's fine:
 * the cookie jar carries no company cookies for an anonymous
 * visitor, and the backend's {@code AuthenticationFilter}
 * short-circuits on the {@code /api/v1/public/} prefix.
 *
 * <p>The {@code errorInterceptor}'s {@code NO_RETRY_PREFIXES}
 * list explicitly includes {@code /api/v1/public/} so a 401 on
 * this path is NOT retried with refresh + NOT treated as a
 * forced-logout trigger. Retrying would force-logout a
 * perfectly valid anonymous visitor. A 404 here is the
 * expected response when the tracking id does not match any
 * shipment (backend returns {@code TRACKING_NOT_FOUND} with
 * the canonical Spanish copy surfaced by the interceptor).
 *
 * <p>Note: the route pattern is {@code /api/v1/public/track/{lgstId}}
 * — the {@code lgstId} path variable is the public tracking
 * code, e.g. {@code "LGST-A3K9P2RX"}.
 */
@Injectable({ providedIn: 'root' })
export class PublicTrackService {
  private readonly http = inject(HttpClient);

  /** {@code GET /api/v1/public/track/{trackingId}}. */
  track(trackingId: string): Observable<PublicTrackResponse> {
    return this.http.get<PublicTrackResponse>(
      `/api/v1/public/track/${encodeURIComponent(trackingId)}`,
    );
  }
}
