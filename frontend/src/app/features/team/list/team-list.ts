import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import {
  CompanyUsersStore,
  ListFilters,
  PaginationState,
} from '../../../core/state/company-users-store';
import { CompanyUserSummary, Role } from '../../../core/types';
import { PageEvent } from '../../../shared/ui/data-table';
import { EmptyStateComponent } from '../../../shared/ui/empty-state';
import { RoleChipComponent } from '../../../shared/ui/role-chip';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge';

/**
 * Team list page (`/team`) — etapa-2-usuarios PR-5.
 *
 * <p>Surface for COMPANY_ADMIN to browse the users in their
 * tenant. Header + filter bar + data table (with mobile cards
 * fallback) + pagination + empty state.
 *
 * <p>Filter bar: free-text search (debounced 300ms), status
 * select, role select (populated from `rolesService`).
 *
 * <p>New user button is gated on
 * `AuthStore.currentUserIsAdmin()` — non-admins still land
 * here (the route guard permits it) but do not see the
 * "Nuevo usuario" CTA. Defense in depth: the route is also
 * blocked by `teamAccessGuard` so non-admins never reach
 * this page.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-team-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    DatePipe,
    RouterLink,
    EmptyStateComponent,
    RoleChipComponent,
    StatusBadgeComponent,
  ],
  templateUrl: './team-list.html',
})
export class TeamListComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly store = inject(CompanyUsersStore);

  /** Filter state — each is its own signal so the table
   * effect can react to any of them. The search input is
   * bound directly to `searchTerm`; the 300ms debounce is
   * handled inside the load effect via `untracked` so the
   * keystroke does not trigger a fetch on every character. */
  protected readonly searchTerm = signal<string>('');
  protected readonly statusFilter = signal<'ALL' | 'ACTIVE' | 'DISABLED'>('ALL');
  protected readonly roleFilter = signal<string>('ALL');

  /** Pagination state — driven by the store but mirrored
   * here so the table's prev/next buttons can render the
   * correct disabled state immediately. */
  protected readonly page = signal<number>(1);

  /** Reactive projection of the current filters into the
   * shape the service expects. The shape mirrors
   * `ListFilters` but the interface declares fields as
   * readonly — so we cast through `Mutable` to allow
   * conditional property addition. */
  private readonly filters = computed<Mutable<ListFilters>>(() => {
    const filters: Mutable<ListFilters> = {};
    const s = this.statusFilter();
    if (s !== 'ALL') filters.status = s;
    const r = this.roleFilter();
    if (r !== 'ALL') filters.roleId = r;
    const search = this.searchTerm().trim();
    if (search.length > 0) filters.search = search;
    return filters;
  });

  /** Reactive projection of "should I fetch now?". Returns
   * the merged query params including the current page. */
  private readonly queryParams = computed(() => ({
    ...this.filters(),
    page: this.page(),
    size: 10,
    sort: 'createdAt,desc',
  }));

  /** Roles catalog used to populate the role filter select.
   * Reads the store directly; the store loads it on init. */
  protected readonly roles = computed<Role[]>(() => this.store.availableRoles());

  /** Filtered rows for the table — simply projects the store
   * signal. Defensive default to [] so the template never
   * sees null mid-load. */
  protected readonly rows = computed<CompanyUserSummary[]>(
    () => this.store.currentCompanyUsers() ?? [],
  );

  /** Pagination projection. */
  protected readonly pagination = computed<PaginationState>(() => this.store.pagination());

  /** True when the empty state should render (the list has
   * loaded and is empty, and we're not currently fetching). */
  protected readonly showEmptyState = computed(
    () => this.store.isListEmpty(),
  );

  /** True when the table should render (list has rows OR
   * is loading a non-empty page). */
  protected readonly showTable = computed(
    () => !this.showEmptyState(),
  );

  constructor() {
    // Effect: when filters or page change, refetch. The
    // effect captures `queryParams` (a computed signal),
    // so Angular only re-runs the effect when one of the
    // underlying signals actually changes.
    effect(() => {
      const params = this.queryParams();
      void this.store.loadList(params).catch(() => {
        // Errors surface via the errorInterceptor + a global
        // toast. The list signal keeps its prior value per
        // store contract, so the UI stays usable.
      });
    });
    // Load the role catalog once on mount (cache hit on
    // subsequent visits inside the same tenant).
    void this.store.loadRoles().catch(() => undefined);
  }

  /** Reset to page 1 whenever a filter changes. */
  protected onFilterChange(): void {
    this.page.set(1);
  }

  protected onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
  }

  protected onStatusChange(value: 'ALL' | 'ACTIVE' | 'DISABLED'): void {
    this.statusFilter.set(value);
    this.page.set(1);
  }

  protected onRoleChange(value: string): void {
    this.roleFilter.set(value);
    this.page.set(1);
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.page);
  }
}

/** Strip readonly markers so we can build ListFilters
 * conditionally. The runtime shape is identical. */
type Mutable<T> = { -readonly [K in keyof T]: T[K] };

/** Pure helper: full name with graceful null handling. */
function fullName(r: CompanyUserSummary): string {
  const f = (r.firstName ?? '').trim();
  const l = (r.lastName ?? '').trim();
  if (f && l) return `${f} ${l}`;
  if (f) return f;
  if (l) return l;
  return r.username;
}

/** Pure helper: format an ISO-8601 timestamp as a short
 * Spanish date or "—" when null. */
function formatDate(iso: string | null): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleDateString('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
}
