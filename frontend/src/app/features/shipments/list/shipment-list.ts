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
  ShipmentsStore,
  ShipmentPaginationState,
} from '../../../core/state/shipments-store';
import { ShipmentListFilters, ShipmentStatus, ShipmentSummary } from '../../../core/types';
import { PageEvent } from '../../../shared/ui/data-table';
import { EmptyStateComponent } from '../../../shared/ui/empty-state';
import { ShipmentStatusBadgeComponent } from '../../../shared/ui/shipment-status-badge';

/** All {@link ShipmentStatus} values, surfaced in the status
 * filter select. Order follows the lifecycle stages defined in
 * the design system (primary → secondary → tertiary → warning
 * → error) so the dropdown reads the same way the badges
 * group them. */
const STATUSES_FOR_FILTER: readonly ShipmentStatus[] = [
  'PRE_ALTA',
  'ESPERANDO_APROBACION_RUTA',
  'CREADO',
  'RECIBIDO_EN_SUCURSAL_ORIGEN',
  'RETENIDO_DOCUMENTACION',
  'CLASIFICADO',
  'EN_TRANSITO_A_HUB',
  'EN_HUB',
  'EN_TRANSITO_CON_ALIADO',
  'EN_TRANSITO_A_DESTINO',
  'RECIBIDO_EN_SUCURSAL_DESTINO',
  'EN_REPARTO',
  'ENTREGADO',
  'ENTREGADO_PARCIAL',
  'ENTREGA_FALLIDA',
  'INCIDENTE_ACTIVO',
  'DEVOLUCION_INICIADA',
  'RETENIDO',
  'DEVUELTO',
  'CANCELADO',
];

/**
 * Shipment list page (`/auth/shipments`) — etapa-3-envios
 * PR-7 (Chunk A — list + detail only).
 *
 * <p>Paginated table of shipments for the signed-in tenant.
 * Header + filter bar (search + status) + data table +
 * pagination + empty state. The status filter exposes every
 * {@link ShipmentStatus} value so operators can drill into
 * a single stage of the lifecycle.
 *
 * <p>Field semantics:
 * <ul>
 *   <li>{@code trackingId} (e.g. {@code LGST-A3K9P2RX}) renders
 *       in monospace so the LGST code is visually
 *       distinguishable from the rest of the row.</li>
 *   <li>{@code status} renders through
 *       {@code <app-shipment-status-badge>} which maps the
 *       internal enum to the design-system color tokens.</li>
 * </ul>
 *
 * <p>Buttons: "+ Nuevo envío" is gated on ADMIN OR OPERATOR
 * (both roles can create per the backend contract). VIEWER
 * and DRIVER do not see the CTA — they browse but cannot
 * create.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-shipment-list',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, EmptyStateComponent, ShipmentStatusBadgeComponent],
  templateUrl: './shipment-list.html',
})
export class ShipmentListComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly store = inject(ShipmentsStore);
  private readonly router = inject(Router);

  /** All statuses surfaced in the filter dropdown. Exposed
   * to the template; declared as a readonly tuple so the
   * template can iterate without TypeScript losing the
   * {@link ShipmentStatus} type. */
  protected readonly statusOptions = STATUSES_FOR_FILTER;

  /** Filter state — each is its own signal so the load effect
   * can react to any of them. `statusFilter === 'ALL'` means
   * "no status filter applied" — the request goes out without
   * a `status` query param. */
  protected readonly searchTerm = signal<string>('');
  protected readonly statusFilter = signal<'ALL' | ShipmentStatus>('ALL');

  /** Pagination state — mirrored from the store so the
   * table's prev/next buttons render the correct disabled
   * state immediately. */
  protected readonly page = signal<number>(1);

  /** True when the current viewer can create a shipment
   * (COMPANY_ADMIN or COMPANY_OPERATOR). VIEWER and DRIVER
   * do not see the CTA. */
  protected readonly canCreate = computed<boolean>(() => {
    const roles = this.authStore.currentUserRoles();
    return roles.includes('COMPANY_ADMIN') || roles.includes('COMPANY_OPERATOR');
  });

  /** Reactive projection of the current filters into the
   * shape the service expects. */
  private readonly filters = computed<Mutable<ShipmentListFilters>>(() => {
    const filters: Mutable<ShipmentListFilters> = {};
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
  protected readonly rows = computed<ShipmentSummary[]>(
    () => this.store.currentShipments() ?? [],
  );

  /** Pagination projection. */
  protected readonly pagination = computed<ShipmentPaginationState>(
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

  /** Navigate to the shipment-create wizard. The wizard
   * ships in PR-7 Chunk B — the link is wired but the route
   * is registered separately. */
  protected goToCreateShipment(): void {
    void this.router.navigate(['/auth/shipments/new']);
  }

  /** Navigate to the detail page when a row link is clicked. */
  goToDetail(id: string): void {
    void this.router.navigate(['/auth/shipments', id]);
  }

  protected onSearchChange(value: string): void {
    this.searchTerm.set(value);
    this.page.set(1);
  }

  protected onStatusChange(value: 'ALL' | ShipmentStatus): void {
    this.statusFilter.set(value);
    this.page.set(1);
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.page);
  }

  /** Pure helper: format an ISO-8601 timestamp as a short
   * Spanish date+time or "—" when null. */
  protected formatDateTime(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}

/** Strip readonly markers so we can build ShipmentListFilters
 * conditionally. The runtime shape is identical. */
type Mutable<T> = { -readonly [K in keyof T]: T[K] };