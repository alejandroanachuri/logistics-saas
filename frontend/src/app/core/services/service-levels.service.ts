import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { ServiceLevel } from '../types';

/**
 * ServiceLevelsService — typed wrapper over the read-only
 * catalog endpoint {@code GET /api/v1/service-levels}.
 * Returns the tenant's active service levels ordered by code.
 * Auth: any authenticated company user. Mutation is Etapa-4
 * scope.
 *
 * <p>The catalog is small (typically 3-5 service levels per
 * tenant) so the response is returned as a flat array, not a
 * {@code PageResponse}. The service is consumed once at
 * shipment-form mount time via the wizard store.
 */
@Injectable({ providedIn: 'root' })
export class ServiceLevelsService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/service-levels';

  /** {@code GET /api/v1/service-levels}. */
  list(): Observable<ServiceLevel[]> {
    return this.http.get<ServiceLevel[]>(ServiceLevelsService.BASE);
  }
}
