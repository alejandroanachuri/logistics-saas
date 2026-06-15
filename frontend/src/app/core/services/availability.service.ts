import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import { AvailabilityResponse } from '../types';

/**
 * Typed wrapper over the three "is this value available"
 * endpoints the registration wizard consumes. The wizard
 * adds a 300ms {@code debounceTime} in the async validators
 * (per {@code wizard-registration.md}); this service
 * intentionally does NOT debounce so the same call shape
 * can be reused from non-debounced call sites in v2.
 */
@Injectable({ providedIn: 'root' })
export class AvailabilityService {
  private readonly http = inject(HttpClient);

  /**
   * {@code GET /api/v1/tenants/me/slug-availability?slug=...}
   */
  checkSlug(slug: string): Observable<AvailabilityResponse> {
    return this.http.get<AvailabilityResponse>('/api/v1/tenants/me/slug-availability', {
      params: new HttpParams().set('slug', slug),
    });
  }

  /**
   * {@code GET /api/v1/tenants/me/cuit-availability?cuit=...}
   */
  checkCuit(cuit: string): Observable<AvailabilityResponse> {
    return this.http.get<AvailabilityResponse>('/api/v1/tenants/me/cuit-availability', {
      params: new HttpParams().set('cuit', cuit),
    });
  }

  /**
   * {@code GET /api/v1/tenants/me/username-availability?slug=...&username=...}
   * — the username is unique per (tenantSlug, username), so the
   * slug is required alongside the username.
   */
  checkUsername(slug: string, username: string): Observable<AvailabilityResponse> {
    return this.http.get<AvailabilityResponse>('/api/v1/tenants/me/username-availability', {
      params: new HttpParams().set('slug', slug).set('username', username),
    });
  }
}
