import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { Subject, debounceTime, distinctUntilChanged, switchMap } from 'rxjs';

import { CustomersService } from '../../../core/services/customers.service';
import { Customer, CustomerSummary } from '../../../core/types';
import { maskDni } from '../../../core/utils/field-level-security';

/** Which side of the shipment a customer picker is filling.
 * Used by {@link ShipmentCreateStep1CustomersComponent.selectCustomer}
 * to disambiguate the two pickers. */
export type CustomerRole = 'sender' | 'receiver';

/**
 * Step 1 of the shipment-create wizard (etapa-3-envios PR-7 Chunk B).
 *
 * <p>Renders two customer pickers — one for the sender and one for the
 * receiver. Each picker has its own debounced search box that hits
 * {@link CustomersService.list} with the typed query. Selecting a
 * customer locks the picker into a "selected" state with a "Cambiar"
 * button that clears the selection.
 *
 * <p>The {@link canAdvance} computed gates the wizard's "Siguiente"
 * button: true only when both sides have a non-null selection AND
 * the ids differ. The backend enforces the same rule with a 422
 * {@code SENDER_EQUALS_RECEIVER} on POST; the client-side guard
 * exists to give the user immediate feedback before the round-trip.
 *
 * <p>Standalone, OnPush, signal-first. Does NOT own the wizard
 * state — the parent {@code ShipmentCreateComponent} reads
 * {@link senderSelection} and {@link receiverSelection} via
 * {@code @ViewChild} when the user clicks Siguiente.
 */
@Component({
  selector: 'app-shipment-create-step-1-customers',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  templateUrl: './shipment-create-step-1-customers.html',
})
export class ShipmentCreateStep1CustomersComponent {
  private readonly customersService = inject(CustomersService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  /** Sender selection (the from-side of the shipment). */
  readonly senderSelection = signal<CustomerSummary | null>(null);

  /** Receiver selection (the to-side of the shipment). */
  readonly receiverSelection = signal<CustomerSummary | null>(null);

  /** Sender search term (drives the debounced fetch). */
  private readonly senderQuery = signal<string>('');
  /** Receiver search term (drives the debounced fetch). */
  private readonly receiverQuery = signal<string>('');

  /** Subject that fires on every typed character in the sender
   * search box. Debounced 300ms before hitting the service. */
  private readonly senderQuery$ = new Subject<string>();
  /** Same, for the receiver. */
  private readonly receiverQuery$ = new Subject<string>();

  /** Reactive result of the sender search — converted to a signal
   * via {@code toSignal} so the template can render the result list. */
  private readonly senderResults = toSignal(
    this.senderQuery$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) =>
        this.customersService.list({
          search: q,
          status: 'ACTIVE',
          page: 1,
          size: 10,
        }),
      ),
    ),
    { initialValue: null },
  );

  /** Same, for the receiver. */
  private readonly receiverResults = toSignal(
    this.receiverQuery$.pipe(
      debounceTime(300),
      distinctUntilChanged(),
      switchMap((q) =>
        this.customersService.list({
          search: q,
          status: 'ACTIVE',
          page: 1,
          size: 10,
        }),
      ),
    ),
    { initialValue: null },
  );

  /** Pulled out as readonly signals so the template can render the
   * rows directly. */
  protected readonly senderRows = computed<CustomerSummary[]>(
    () => this.senderResults()?.data ?? [],
  );
  protected readonly receiverRows = computed<CustomerSummary[]>(
    () => this.receiverResults()?.data ?? [],
  );

  /** Wizard navigation gate: both sides selected AND sender !== receiver. */
  readonly canAdvance = computed<boolean>(() => {
    const s = this.senderSelection();
    const r = this.receiverSelection();
    if (!s || !r) return false;
    return s.id !== r.id;
  });

  /** Bind a typed character in the sender search input. */
  protected onSenderSearchInput(value: string): void {
    this.senderQuery.set(value);
    this.senderQuery$.next(value);
  }

  /** Bind a typed character in the receiver search input. */
  protected onReceiverSearchInput(value: string): void {
    this.receiverQuery.set(value);
    this.receiverQuery$.next(value);
  }

  /** Lock a customer into the matching side of the picker. */
  selectCustomer(customer: CustomerSummary, role: CustomerRole): void {
    if (role === 'sender') {
      this.senderSelection.set(customer);
    } else {
      this.receiverSelection.set(customer);
    }
  }

  /** Clear the matching side (the "Cambiar" button). */
  clearSelection(role: CustomerRole): void {
    if (role === 'sender') {
      this.senderSelection.set(null);
    } else {
      this.receiverSelection.set(null);
    }
  }

  /**
   * Build the link to the customer-create page for a given side,
   * so the operator can create a new customer inline without
   * leaving the wizard. The {@code returnTo} query param is the
   * absolute path of this wizard; after creating, the
   * customer-create page navigates back with {@code ?preSelected=}
   * pointing at the new customer, and the wizard pre-fills the
   * matching picker.
   */
  createCustomerLink(role: CustomerRole): { path: string[]; queryParams: Record<string, string> } {
    const returnTo = this.router.getCurrentNavigation()?.finalUrl?.toString()
      ?? this.router.url
      ?? '/auth/shipments/new';
    const queryParams: Record<string, string> = {
      returnTo,
      role,
    };
    return {
      path: ['/auth/customers/new'],
      queryParams,
    };
  }

  /**
   * Pre-fill the matching picker with a customer that was just
   * created (or any other Customer that was passed back from a
   * round-trip). Used by the orchestrator to consume the
   * {@code ?preSelected=} query param. Accepts the full
   * {@link Customer} from the detail endpoint and maps to the
   * {@link CustomerSummary} shape the picker stores.
   */
  preSelectFromDetail(customer: Customer, role: CustomerRole): void {
    const name = customer.razonSocial
      || [customer.firstName, customer.lastName].filter(Boolean).join(' ')
      || '—';
    const summary: CustomerSummary = {
      id: customer.id,
      personType: customer.personType,
      name,
      email: customer.email,
      phone: customer.phone,
      dni: customer.dni,
      cuitCuil: customer.cuitCuil,
      // The detail response doesn't include status; default to ACTIVE
      // for a customer the operator just created.
      status: 'ACTIVE',
      createdAt: customer.createdAt,
    };
    if (role === 'sender') {
      this.senderSelection.set(summary);
    } else {
      this.receiverSelection.set(summary);
    }
  }

  /**
   * Display-friendly customer name already projected by the backend
   * summary DTO.
   */
  displayName(row: CustomerSummary): string {
    const name = row.name?.trim();
    return name && name.length > 0 ? name : '—';
  }

  /** Mask the DNI for display in the picker results. The backend
   * already masks per JsonView for masked roles, but the wizard
   * applies a defensive client-side mask too. */
  displayDni(dni: string | null): string {
    if (!dni) return '—';
    return maskDni(dni) ?? '—';
  }
}
