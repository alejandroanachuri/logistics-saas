import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { Address, CreateAddressRequest, UpdateAddressRequest } from '../types';

/**
 * AddressesService — typed wrapper over the 3 endpoints under
 * {@code /api/v1/addresses/*}: GET (detail), POST (create),
 * PATCH (partial update). Disable ships in PR-3b together with
 * the customer / address attachment cascade.
 *
 * <p>Mirrors the {@code CompanyUsersService} pattern: thin
 * HttpClient calls, all error mapping delegated to the global
 * {@code errorInterceptor}. Auth: any authenticated company
 * user can read; ADMIN+OPERATOR can write.
 *
 * <p>The list endpoint is intentionally absent — there is no
 * "list all addresses for the tenant" UI in v1. The customer
 * picker and the shipment form look up addresses through
 * customer-scoped endpoints, not through this service.
 */
@Injectable({ providedIn: 'root' })
export class AddressesService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/addresses';

  /** {@code GET /api/v1/addresses/{id}}. */
  get(id: string): Observable<Address> {
    return this.http.get<Address>(`${AddressesService.BASE}/${id}`);
  }

  /** {@code POST /api/v1/addresses}. Returns the freshly-created
   * detail. Backend responds with 201 Created. */
  create(req: CreateAddressRequest): Observable<Address> {
    return this.http.post<Address>(AddressesService.BASE, req);
  }

  /** {@code PATCH /api/v1/addresses/{id}}. Partial update —
   * every field optional, backend applies a per-field
   * allowlist. */
  update(id: string, req: UpdateAddressRequest): Observable<Address> {
    return this.http.patch<Address>(`${AddressesService.BASE}/${id}`, req);
  }
}
