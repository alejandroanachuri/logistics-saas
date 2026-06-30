import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import { CustomersStore } from '../../../core/state/customers-store';
import { Customer, CustomerTaxCondition } from '../../../core/types';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog';
import {
  maskCuit,
  maskDni,
  shouldMaskSensitiveFields,
} from '../../../core/utils/field-level-security';

/**
 * Customer detail page (`/auth/customers/:id`) — etapa-3-envios
 * PR-6 Chunk A.
 *
 * <p>Read-only view of a single customer record. The header
 * shows the customer name (or razon social for JURIDICA) +
 * status badge. The body has a 2-column dl grid with the
 * person's contact + identity info.
 *
 * <p>Actions:
 * - "Editar información" → /auth/customers/:id/edit (gated on
 *   COMPANY_ADMIN OR COMPANY_OPERATOR).
 * - "Desactivar" → opens a confirm dialog, calls
 *   {@code customers-store.disable(id)} (gated on COMPANY_ADMIN
 *   only — disable is admin-only per the backend contract).
 *
 * <p>Field-level security: DNI/CUIT are masked for the
 * role-elevated viewer (COMPANY_VIEWER, COMPANY_DRIVER) per the
 * JsonView rules + the defensive client-side
 * {@code field-level-security} helper.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-customer-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, ConfirmDialogComponent],
  templateUrl: './customer-detail.html',
})
export class CustomerDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(CustomersStore);
  protected readonly authStore = inject(AuthStore);

  /** Target customer id resolved from the route param map. */
  protected readonly targetCustomerId = signal<string>('');

  /** Loaded detail. */
  protected readonly customer = signal<Customer | null>(null);

  /** Local UI state. */
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);
  readonly showDisableConfirm = signal<boolean>(false);

  /** True when the current viewer should see masked DNI/CUIT. */
  protected readonly maskSensitive = computed<boolean>(() =>
    shouldMaskSensitiveFields(this.authStore.currentUserRoles()),
  );

  /** True when the current viewer can edit the customer
   * (COMPANY_ADMIN OR COMPANY_OPERATOR — both roles are
   * authorized to PATCH per the backend contract). */
  protected readonly canEdit = computed<boolean>(() => {
    const roles = this.authStore.currentUserRoles();
    return roles.includes('COMPANY_ADMIN') || roles.includes('COMPANY_OPERATOR');
  });

  /** True when the current viewer can disable the customer
   * (COMPANY_ADMIN only — disable is admin-only per the
   * backend contract). The detail type does not expose status;
   * the disable button is shown unconditionally when the
   * viewer is admin. The backend rejects re-disabling an
   * already-disabled customer with 409. */
  protected readonly canDisable = computed<boolean>(
    () => this.authStore.currentUserIsAdmin(),
  );

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.targetCustomerId.set(id);
    if (!id) {
      void this.router.navigate(['/auth/customers']);
      return;
    }
    this.loadDetail();
  }

  /** Fetch the detail via the store. */
  private loadDetail(): void {
    const id = this.targetCustomerId();
    if (!id) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    void this.store
      .loadDetail(id)
      .then((detail) => {
        this.customer.set(detail);
        this.isLoading.set(false);
      })
      .catch(() => {
        this.isLoading.set(false);
        this.errorMessage.set('No pudimos cargar el cliente. Probá de nuevo.');
      });
  }

  /** Navigate to the edit page. */
  protected editInfo(): void {
    const id = this.targetCustomerId();
    if (!id) return;
    void this.router.navigate(['/auth/customers', id, 'edit']);
  }

  /** Disable the customer after the confirm dialog fires. */
  async disable(): Promise<void> {
    this.showDisableConfirm.set(false);
    const id = this.targetCustomerId();
    if (!id) return;
    try {
      await this.store.disable(id);
      // Refetch so the status badge flips to DISABLED.
      this.loadDetail();
    } catch {
      this.errorMessage.set('No pudimos desactivar al cliente. Probá de nuevo.');
    }
  }

  /** Pure helper exposed to the template for the H1. */
  protected displayName(detail: Customer | null): string {
    if (!detail) return '';
    if (detail.razonSocial) return detail.razonSocial;
    const f = (detail.firstName ?? '').trim();
    const l = (detail.lastName ?? '').trim();
    if (f && l) return `${f} ${l}`;
    if (f) return f;
    if (l) return l;
    return '—';
  }

  /** Returns the display-friendly DNI: full value when the
   * viewer is ADMIN / OPERATOR, masked otherwise. */
  protected displayDni(dni: string | null): string {
    if (!dni) return '—';
    return this.maskSensitive() ? (maskDni(dni) ?? '—') : dni;
  }

  /** Returns the display-friendly CUIT. */
  protected displayCuit(cuit: string | null): string {
    if (!cuit) return '—';
    return this.maskSensitive() ? (maskCuit(cuit) ?? '—') : cuit;
  }

  /** Spanish label for the personType discriminator. */
  protected displayPersonType(detail: Customer | null): string {
    if (!detail) return '—';
    return detail.personType === 'JURIDICA' ? 'Persona jurídica' : 'Persona física';
  }

  /** Spanish label for the tax condition enum. */
  protected displayTaxCondition(cond: CustomerTaxCondition): string {
    switch (cond) {
      case 'RESPONSABLE_INSCRIPTO':
        return 'Responsable inscripto';
      case 'MONOTRIBUTISTA':
        return 'Monotributista';
      case 'EXENTO':
        return 'Exento';
      case 'CONSUMIDOR_FINAL':
        return 'Consumidor final';
      case 'NO_CATEGORIZADO':
        return 'No categorizado';
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

  /** Spanish copy for the consent trail. */
  protected consentLabel(detail: Customer | null): string {
    if (!detail) return '—';
    return detail.dataConsent
      ? `Aceptado el ${this.formatDate(detail.consentDate)}`
      : 'No aceptado';
  }
}
