import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import {
  CustomersStore,
  CustomerPaginationState,
} from '../../../core/state/customers-store';
import { CustomerListFilters, CustomerSummary } from '../../../core/types';
import { PageEvent } from '../../../shared/ui/data-table';
import { EmptyStateComponent } from '../../../shared/ui/empty-state';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge';
import {
  maskCuit,
  maskDni,
  shouldMaskSensitiveFields,
} from '../../../core/utils/field-level-security';

/**
 * Customer list page (`/auth/customers`) — etapa-3-envios PR-6 Chunk A.
 *
 * <p>Surface for COMPANY_ADMIN and COMPANY_OPERATOR to browse
 * the customer records of their tenant. Header + filter bar +
 * data table + pagination + empty state. Field-level security
 * (DNI / CUIT masking) is applied via
 * {@code shouldMaskSensitiveFields()} for the role-elevated
 * viewer (COMPANY_VIEWER, COMPANY_DRIVER) — the backend's
 * JsonView already masks these on most list responses, but the
 * frontend applies a defensive belt-and-braces mask here.
 *
 * <p>Buttons: "+ Nuevo cliente" is gated on ADMIN OR OPERATOR
 * (both roles can create per the backend contract). "Editar"
 * and "Desactivar" are wired in {@code customer-detail}, not
 * here.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-customer-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EmptyStateComponent, StatusBadgeComponent],
  templateUrl: './customer-list.html',
})
export class CustomerListComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly store = inject(CustomersStore);
  private readonly router = inject(Router);

  /** Filter state — each is its own signal so the load effect
   * can react to any of them. */
  protected readonly searchTerm = signal<string>('');
  protected readonly statusFilter = signal<'ALL' | 'ACTIVE' | 'DISABLED'>('ACTIVE');

  /** Pagination state — driven by the store but mirrored here
   * so the table's prev/next buttons can render the correct
   * disabled state immediately. */
  protected readonly page = signal<number>(1);

  /** True when the current viewer should see masked DNI/CUIT
   * (defensive client-side mask for COMPANY_VIEWER /
   * COMPANY_DRIVER). */
  protected readonly maskSensitive = computed<boolean>(() =>
    shouldMaskSensitiveFields(this.authStore.currentUserRoles()),
  );

  /** True when the current viewer can create a customer
   * (COMPANY_ADMIN or COMPANY_OPERATOR). */
  protected readonly canCreate = computed<boolean>(() => {
    const roles = this.authStore.currentUserRoles();
    return roles.includes('COMPANY_ADMIN') || roles.includes('COMPANY_OPERATOR');
  });

  /** Reactive projection of the current filters into the
   * shape the service expects. */
  private readonly filters = computed<Mutable<CustomerListFilters>>(() => {
    const filters: Mutable<CustomerListFilters> = {};
    const s = this.statusFilter();
    if (s !== 'ALL') filters.status = s;
    const search = this.searchTerm().trim();
    if (search.length > 0) filters.search = search;
    return filters;
  });

  /** Reactive projection of "should I fetch now?". */
  private readonly queryParams = computed(() => ({
    ...this.filters(),
    page: this.page(),
    size: 20,
    sort: 'createdAt,desc',
  }));

  /** Filtered rows for the table — defensive default to []. */
  protected readonly rows = computed<CustomerSummary[]>(
    () => this.store.currentCustomers() ?? [],
  );

  /** Pagination projection. */
  protected readonly pagination = computed<CustomerPaginationState>(
    () => this.store.pagination(),
  );

  /** True when the empty state should render. */
  protected readonly showEmptyState = computed(() => this.store.isListEmpty());

  /** True when the table should render (list has rows OR
   * is loading a non-empty page). */
  protected readonly showTable = computed(() => !this.showEmptyState());

  constructor() {
    // Effect: when filters or page change, refetch.
    effect(() => {
      const params = this.queryParams();
      void this.store.loadList(params).catch(() => {
        // Errors surface via the errorInterceptor + a global
        // toast. The list signal keeps its prior value per
        // store contract, so the UI stays usable.
      });
    });
  }

  /** Reset to page 1 whenever a filter changes. */
  protected onFilterChange(): void {
    this.page.set(1);
  }

  /** Navigate to the customer-create page. Wired to the
   * empty-state CTA so admins/operators can jump straight into
   * the create flow from the list view. */
  protected goToCreateCustomer(): void {
    this.router.navigate(['/auth/customers/new']);
  }

  protected onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
  }

  protected onStatusChange(value: 'ALL' | 'ACTIVE' | 'DISABLED'): void {
    this.statusFilter.set(value);
    this.page.set(1);
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.page);
  }

  /** Returns the display-friendly DNI: full value when the
   * viewer is ADMIN / OPERATOR, masked otherwise (defensive
   * client-side mask — the backend already masks per JsonView
   * for the masked-role list response). */
  protected displayDni(dni: string | null): string {
    if (!dni) return '—';
    return this.maskSensitive() ? (maskDni(dni) ?? '—') : dni;
  }

  /** Returns the display-friendly CUIT: full value when the
   * viewer is ADMIN / OPERATOR, masked otherwise. */
  protected displayCuit(cuit: string | null): string {
    if (!cuit) return '—';
    return this.maskSensitive() ? (maskCuit(cuit) ?? '—') : cuit;
  }

  /**
   * Returns the display-ready customer name already projected by the
   * backend list DTO.
   */
  protected displayName(row: CustomerSummary): string {
    const name = row.name?.trim();
    return name && name.length > 0 ? name : '—';
  }

  /** Spanish label for the personType discriminator. */
  protected displayPersonType(row: CustomerSummary): string {
    return row.personType === 'JURIDICA' ? 'Persona jurídica' : 'Persona física';
  }

  /** Friendly phone cell — empty string from the API still renders as —. */
  protected displayPhone(row: CustomerSummary): string {
    const phone = row.phone?.trim();
    return phone && phone.length > 0 ? phone : '—';
  }
}

/** Strip readonly markers so we can build CustomerListFilters
 * conditionally. The runtime shape is identical. */
type Mutable<T> = { -readonly [K in keyof T]: T[K] };
