import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

import {
  Customer,
  CustomerListFilters,
  CustomerSummary,
  CreateCustomerRequest,
  PageResponse,
  UpdateCustomerRequest,
} from '../types';

/**
 * CustomersService — typed wrapper over the 5 admin endpoints
 * under {@code /api/v1/customers/*}. Mirrors the
 * {@code CompanyUsersService} pattern: thin HttpClient calls,
 * all error mapping delegated to the global
 * {@code errorInterceptor}.
 *
 * <p>The customer record is shared between the team-management
 * module and the shipment module (sender + receiver are both
 * customers). The list view returns a {@code CustomerSummary}
 * projection with masked DNI/CUIT per the backend's JsonView
 * rules; the detail view returns the full {@code Customer}.
 *
 * <p>Auth (per backend controller):
 * <ul>
 *   <li>list / get — ADMIN, OPERATOR, VIEWER</li>
 *   <li>create / update — ADMIN, OPERATOR</li>
 *   <li>disable — ADMIN only (the service does not enforce this;
 *       the backend rejects with 403 + INSUFFICIENT_PERMISSIONS
 *       and the interceptor surfaces the canonical Spanish copy)</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class CustomersService {
  private readonly http = inject(HttpClient);
  private static readonly BASE = '/api/v1/customers';

  /**
   * {@code GET /api/v1/customers?page=...&size=...&sort=...&search=...&status=...}.
   * Returns the paged envelope with masked DNI/CUIT per the
   * backend's JsonView rules.
   */
  list(
    filters: CustomerListFilters & { page?: number; size?: number; sort?: string },
  ): Observable<PageResponse<CustomerSummary>> {
    let httpParams = new HttpParams();
    if (filters.page !== undefined) httpParams = httpParams.set('page', String(filters.page));
    if (filters.size !== undefined) httpParams = httpParams.set('size', String(filters.size));
    if (filters.sort !== undefined) httpParams = httpParams.set('sort', filters.sort);
    if (filters.search !== undefined) httpParams = httpParams.set('search', filters.search);
    if (filters.status !== undefined) httpParams = httpParams.set('status', filters.status);

    return this.http.get<PageResponse<CustomerSummary>>(CustomersService.BASE, {
      params: httpParams,
    });
  }

  /** {@code GET /api/v1/customers/{id}}. */
  get(id: string): Observable<Customer> {
    return this.http.get<Customer>(`${CustomersService.BASE}/${id}`);
  }

  /** {@code POST /api/v1/customers}. Returns the freshly-created
   * detail (no temporary password envelope — customer records do
   * not provision credentials). */
  create(req: CreateCustomerRequest): Observable<Customer> {
    return this.http.post<Customer>(CustomersService.BASE, req);
  }

  /** {@code PATCH /api/v1/customers/{id}}. Partial update —
   * personType is NOT updatable. */
  update(id: string, req: UpdateCustomerRequest): Observable<Customer> {
    return this.http.patch<Customer>(`${CustomersService.BASE}/${id}`, req);
  }

  /** {@code POST /api/v1/customers/{id}/disable}. Soft-delete
   * (ADMIN only). Returns void (backend responds with 204 No
   * Content). */
  disable(id: string): Observable<void> {
    return this.http.post<void>(`${CustomersService.BASE}/${id}/disable`, null);
  }
}
