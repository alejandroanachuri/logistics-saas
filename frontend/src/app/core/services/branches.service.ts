import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Branch } from '../types';

/**
 * BranchesService — typed wrapper over the read-only catalog
 * endpoint {@code GET /api/v1/branches}. Returns the tenant's
 * active branches ordered by code. Auth: any authenticated
 * company user. Mutation is Etapa-4 scope.
 *
 * <p>The catalog is small (a handful of branches per tenant)
 * so the response is returned as a flat array, not a
 * {@code PageResponse}. The service is consumed once at
 * shipment-form mount time via the wizard store.
 */
@Injectable({ providedIn: 'root' })
export class BranchesService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/branches';

  /** {@code GET /api/v1/branches}. */
  list(): Observable<Branch[]> {
    return this.http.get<Branch[]>(BranchesService.BASE);
  }
}
