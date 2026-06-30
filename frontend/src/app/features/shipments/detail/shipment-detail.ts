import {
  ChangeDetectionStrategy,
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import { ShipmentsStore } from '../../../core/state/shipments-store';
import { ShipmentDetail, ShipmentStatus, TrackingEvent } from '../../../core/types';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog';
import { InputComponent } from '../../../shared/ui/input';
import { PackageStatusBadgeComponent } from '../../../shared/ui/package-status-badge';
import { ShipmentStatusBadgeComponent } from '../../../shared/ui/shipment-status-badge';
import { TrackingTimelineComponent } from '../../../shared/ui/tracking-timeline';

type DetailTab = 'info' | 'packages' | 'timeline';

/** Shipment statuses that block the "Cancelar" action (admin
 * only). Cancel is not meaningful after the shipment has
 * reached a final state — the package is already delivered
 * or returned, or the shipment has already been cancelled. */
const FINAL_STATUSES: readonly ShipmentStatus[] = [
  'ENTREGADO',
  'DEVUELTO',
  'CANCELADO',
];

/**
 * Shipment detail page (`/auth/shipments/:id`) — etapa-3-envios
 * PR-7 (Chunk A — list + detail only).
 *
 * <p>Read-only header (trackingId + status badge + sender →
 * receiver summary) followed by three tabs:
 * <ol>
 *   <li>**Información** — read-only view of every shipment
 *       field: sender / receiver / delivery address / branches
 *       / service level / payment type / delivery mode /
 *       delivery instructions / packages count / total weight /
 *       total cost / created-at.</li>
 *   <li>**Paquetes** — list of {@code ShipmentPackage} records,
 *       each with its own status badge.</li>
 *   <li>**Timeline** — vertical list of {@code TrackingEvent}
 *       entries (flattened across all packages) rendered
 *       through the shared {@code <app-tracking-timeline>}.</li>
 * </ol>
 *
 * <p>Action buttons (gated by role + status):
 * <ul>
 *   <li>**Validar** — ADMIN OR OPERATOR, only when status is
 *       {@code PRE_ALTA}. Calls {@code store.validate(id)} to
 *       transition the shipment PRE_ALTA → CREADO.</li>
 *   <li>**Rechazar** — ADMIN OR OPERATOR, only when status is
 *       {@code PRE_ALTA}. Opens a confirm dialog with a
 *       mandatory rejection reason text input, calls
 *       {@code store.reject(id, reason)}.</li>
 *   <li>**Cancelar** — ADMIN only, when status is not in
 *       {@code FINAL_STATUSES}. Opens a confirm dialog with a
 *       mandatory reason text input, calls
 *       {@code store.cancel(id, reason)}.</li>
 *   <li>**Editar** — ADMIN OR OPERATOR, only when status is
 *       {@code PRE_ALTA}. Navigates to
 *       `/auth/shipments/:id/edit` (the edit page ships in
 *       PR-7 Chunk B).</li>
 * </ul>
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-shipment-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    ConfirmDialogComponent,
    InputComponent,
    PackageStatusBadgeComponent,
    ShipmentStatusBadgeComponent,
    TrackingTimelineComponent,
  ],
  templateUrl: './shipment-detail.html',
})
export class ShipmentDetailComponent implements OnInit, OnDestroy {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(ShipmentsStore);
  protected readonly authStore = inject(AuthStore);

  /** Target shipment id resolved from the route param map. */
  protected readonly targetShipmentId = signal<string>('');

  /** Loaded detail. */
  protected readonly shipment = signal<ShipmentDetail | null>(null);

  /** Local UI state. */
  readonly selectedTab = signal<DetailTab>('info');
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** Modal triggers. The "Rechazar" and "Cancelar" flows need
   * a free-text reason input that the {@code confirm-dialog}
   * primitive does not provide, so we render the input
   * inline inside a wrapper around the confirm dialog. */
  readonly showValidateConfirm = signal<boolean>(false);
  readonly showRejectForm = signal<boolean>(false);
  readonly showCancelForm = signal<boolean>(false);
  readonly rejectionReason = signal<string>('');
  readonly cancelReason = signal<string>('');

  /** True when the current viewer can edit / validate / reject
   * (COMPANY_ADMIN or COMPANY_OPERATOR — backend permits both
   * roles per the controller's @PreAuthorize). */
  protected readonly canMutate = computed<boolean>(() => {
    const roles = this.authStore.currentUserRoles();
    return roles.includes('COMPANY_ADMIN') || roles.includes('COMPANY_OPERATOR');
  });

  /** True only for COMPANY_ADMIN (used for the cancel button
   * which is ADMIN-only per the backend contract). */
  protected readonly isAdmin = computed<boolean>(
    () => this.authStore.currentUserIsAdmin(),
  );

  /** True when the shipment is still in PRE_ALTA — the only
   * state in which validate / reject / edit are permitted. */
  protected readonly isPreAlta = computed<boolean>(
    () => this.shipment()?.status === 'PRE_ALTA',
  );

  /** True when cancel is allowed (admin + non-final). */
  protected readonly canCancel = computed<boolean>(
    () =>
      this.isAdmin() &&
      !FINAL_STATUSES.includes(this.shipment()?.status as ShipmentStatus),
  );

  /** Derived timeline events from the store (single source of
   * truth — the detail page never holds its own copy). */
  protected readonly timelineEvents = computed<TrackingEvent[]>(
    () => this.store.currentTimeline() ?? [],
  );

  /** Derived package count for the info tab. */
  protected readonly packageCount = computed<number>(
    () => this.shipment()?.packages?.length ?? 0,
  );

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.targetShipmentId.set(id);
    if (!id) {
      void this.router.navigate(['/auth/shipments']);
      return;
    }
    this.loadDetail();
  }

  ngOnDestroy(): void {
    // Clear the cached detail + timeline so the next visit to
    // a different shipment does not flash stale data.
    this.store.clearDetail();
  }

  /** Fetch the detail + timeline via the store. */
  private loadDetail(): void {
    const id = this.targetShipmentId();
    if (!id) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    void this.store
      .loadDetail(id)
      .then((detail) => {
        this.shipment.set(detail);
        this.isLoading.set(false);
      })
      .catch(() => {
        this.isLoading.set(false);
        this.errorMessage.set('No pudimos cargar el envío. Probá de nuevo.');
      });
    void this.store
      .loadTimeline(id)
      .catch(() => {
        // Timeline errors are non-fatal — the page still
        // renders; the timeline tab simply shows the
        // component's "Sin eventos registrados." message.
      });
  }

  /** Navigate to the edit page when "Editar" is clicked. */
  goToEdit(): void {
    const id = this.targetShipmentId();
    if (!id) return;
    void this.router.navigate(['/auth/shipments', id, 'edit']);
  }

  /** Run the validate flow. Triggered from the confirm dialog
   * for "Validar". */
  async validate(): Promise<void> {
    this.showValidateConfirm.set(false);
    const id = this.targetShipmentId();
    if (!id) return;
    try {
      const detail = await this.store.validate(id);
      this.shipment.set(detail);
    } catch {
      this.errorMessage.set('No pudimos validar el envío. Probá de nuevo.');
    }
  }

  /** Run the reject flow. Triggered from the reject-form
   * dialog. The reason is mandatory — the backend rejects
   * empty strings with 400 VALIDATION_ERROR. */
  async reject(): Promise<void> {
    const id = this.targetShipmentId();
    const reason = this.rejectionReason().trim();
    if (!id || reason.length === 0) return;
    this.showRejectForm.set(false);
    try {
      const detail = await this.store.reject(id, reason);
      this.shipment.set(detail);
      this.rejectionReason.set('');
    } catch {
      this.errorMessage.set('No pudimos rechazar el envío. Probá de nuevo.');
    }
  }

  /** Run the cancel flow. Triggered from the cancel-form
   * dialog. ADMIN only — the canCancel computed gates the
   * button. */
  async cancel(): Promise<void> {
    const id = this.targetShipmentId();
    const reason = this.cancelReason().trim();
    if (!id || reason.length === 0) return;
    this.showCancelForm.set(false);
    try {
      const detail = await this.store.cancel(id, reason);
      this.shipment.set(detail);
      this.cancelReason.set('');
    } catch {
      this.errorMessage.set('No pudimos cancelar el envío. Probá de nuevo.');
    }
  }

  /** Spanish label for the payment-type discriminator. */
  protected displayPaymentType(paymentType: string): string {
    switch (paymentType) {
      case 'PAGO_ORIGEN':
        return 'Pago en origen';
      case 'PAGO_DESTINO':
        return 'Pago en destino';
      case 'CUENTA_CORRIENTE':
        return 'Cuenta corriente';
      default:
        return paymentType;
    }
  }

  /** Spanish label for the delivery-mode discriminator. */
  protected displayDeliveryMode(mode: string): string {
    switch (mode) {
      case 'DOMICILIO':
        return 'Entrega a domicilio';
      case 'RETIRO_SUCURSAL':
        return 'Retiro en sucursal';
      default:
        return mode;
    }
  }

  /** Pure helper: format an ISO-8601 timestamp as a short
   * Spanish date or "—" when null. */
  protected formatDate(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
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

  /** Format a numeric value as ARS currency or "—" when null. */
  protected formatCurrency(value: number | null): string {
    if (value === null || value === undefined) return '—';
    return new Intl.NumberFormat('es-AR', {
      style: 'currency',
      currency: 'ARS',
      maximumFractionDigits: 2,
    }).format(value);
  }
}