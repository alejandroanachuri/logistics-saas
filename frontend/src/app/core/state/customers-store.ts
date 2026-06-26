import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CustomersService } from '../services/customers.service';
import {
  Customer,
  CustomerListFilters,
  CustomerSummary,
  CreateCustomerRequest,
  PageResponse,
  UpdateCustomerRequest,
} from '../types';

export interface CustomerPaginationState {
  readonly page: number;
  readonly size: number;
  readonly total: number;
}

/**
 * CustomersStore — signal-based state for the customer
 * management pages (etapa-3-envios PR-5). Mirrors the
 * {@code CompanyUsersStore} pattern: signals for derived
 * reads, methods for actions.
 *
 * <p>The store holds the current list (page slice), the
 * currently-loaded detail, the pagination + filter state, and
 * a transient "isSubmitting" flag used by the create / update
 * / disable forms to disable submit buttons during the
 * in-flight call.
 *
 * <p>Error policy: on failure, the relevant signal keeps its
 * previous value (so the UI does not flash empty during an
 * intermittent failure) and the error is rethrown to the
 * caller. Callers catch and surface the localized copy from
 * the {@code errorInterceptor}.
 */
@Injectable({ providedIn: 'root' })
export class CustomersStore {
  private readonly customersService = inject(CustomersService);

  private readonly _currentCustomers = signal<CustomerSummary[] | null>(null);
  private readonly _currentCustomer = signal<Customer | null>(null);
  private readonly _pagination = signal<CustomerPaginationState>({
    page: 1,
    size: 20,
    total: 0,
  });
  private readonly _listFilters = signal<CustomerListFilters>({});
  private readonly _isLoading = signal<boolean>(false);
  private readonly _isSubmitting = signal<boolean>(false);

  readonly currentCustomers = this._currentCustomers.asReadonly();
  readonly currentCustomer = this._currentCustomer.asReadonly();
  readonly pagination = this._pagination.asReadonly();
  readonly listFilters = this._listFilters.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isSubmitting = this._isSubmitting.asReadonly();

  /** Convenience flag: the store has loaded at least one page
   * and the list is currently empty. The customer-list page
   * uses this to decide between {@code <app-data-table>} and
   * {@code <app-empty-state>}. */
  readonly isListEmpty = computed(
    () => !this._isLoading() && (this._currentCustomers()?.length ?? 0) === 0,
  );

  /** Fetch a page of customers and update the list +
   * pagination signals on success. */
  async loadList(
    filters: CustomerListFilters & { page?: number; size?: number; sort?: string },
  ): Promise<void> {
    this._listFilters.set({
      search: filters.search,
      status: filters.status,
    });
    this._isLoading.set(true);
    try {
      const page: PageResponse<CustomerSummary> = await firstValueFrom(
        this.customersService.list(filters),
      );
      this._currentCustomers.set(page.data);
      this._pagination.set({ page: page.page, size: page.size, total: page.total });
    } finally {
      this._isLoading.set(false);
    }
  }

  /** Fetch a single customer detail and update the
   * currentCustomer signal. */
  async loadDetail(id: string): Promise<Customer> {
    const detail = await firstValueFrom(this.customersService.get(id));
    this._currentCustomer.set(detail);
    return detail;
  }

  /** Create a new customer. Returns the created detail. */
  async create(req: CreateCustomerRequest): Promise<Customer> {
    this._isSubmitting.set(true);
    try {
      return await firstValueFrom(this.customersService.create(req));
    } finally {
      this._isSubmitting.set(false);
    }
  }

  /** Update an existing customer. Returns the updated detail. */
  async update(id: string, req: UpdateCustomerRequest): Promise<Customer> {
    this._isSubmitting.set(true);
    try {
      const updated = await firstValueFrom(this.customersService.update(id, req));
      // Keep the cached detail in sync so the detail page does
      // not flash stale data after a successful update.
      if (this._currentCustomer()?.id === id) {
        this._currentCustomer.set(updated);
      }
      return updated;
    } finally {
      this._isSubmitting.set(false);
    }
  }

  /** Disable a customer. Returns void; caller refetches the
   * detail or list to refresh the view. */
  async disable(id: string): Promise<void> {
    this._isSubmitting.set(true);
    try {
      await firstValueFrom(this.customersService.disable(id));
    } finally {
      this._isSubmitting.set(false);
    }
  }

  /** Clear the cached detail (used by the customer-edit /
   * customer-detail pages when navigating away). */
  clearDetail(): void {
    this._currentCustomer.set(null);
  }
}
