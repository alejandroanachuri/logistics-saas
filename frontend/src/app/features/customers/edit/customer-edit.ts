import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import { CustomersStore } from '../../../core/state/customers-store';
import {
  CustomerFormComponent,
  CustomerFormData,
} from '../../../shared/ui/customer-form';
import { Customer, UpdateCustomerRequest } from '../../../core/types';

/**
 * Customer edit page (`/auth/customers/:id/edit`) — etapa-3-envios
 * PR-6 Chunk A.
 *
 * <p>Wraps the shared {@code CustomerFormComponent} primitive
 * (PR-5) in pre-fill mode. The page:
 *
 * <ol>
 *   <li>Loads the customer detail via the store in {@code ngOnInit}.</li>
 *   <li>Reads the resulting customer from {@code store.currentCustomer}
 *       via an effect and pushes it into the shared form's
 *       {@code customer} input (the form pre-fills from that).</li>
 *   <li>On submit, builds an {@code UpdateCustomerRequest} (the
 *       PATCH body — no {@code personType} field, which is
 *       immutable per the backend contract) and calls
 *       {@code customers-store.update()}.</li>
 *   <li>Navigates back to the detail page on success; clears
 *       the store's cached detail on cancel and on submit.</li>
 * </ol>
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-customer-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CustomerFormComponent],
  templateUrl: './customer-edit.html',
})
export class CustomerEditComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(CustomersStore);
  protected readonly authStore = inject(AuthStore);

  /** Target customer id resolved from the route param map. */
  protected readonly targetCustomerId = signal<string>('');

  /** Local UI state. */
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  /** Loaded detail — passed to the shared form's `customer`
   * input as a signal. The shared form's effect picks it up
   * and pre-fills its internal form. */
  protected readonly customer = computed<Customer | null>(
    () => this.store.currentCustomer(),
  );

  constructor() {
    // Re-render the shared form whenever the store signal
    // changes. The shared form's effect does the patchValue.
    // We declare this dependency so OnPush picks up the change
    // and the template refreshes the [customer] binding.
    effect(() => {
      void this.customer();
    });
  }

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.targetCustomerId.set(id);
    if (!id) {
      void this.router.navigate(['/auth/customers']);
      return;
    }
    void this.store
      .loadDetail(id)
      .catch(() => {
        // errorInterceptor surfaces the localized copy; bounce
        // back to the list page so the user can pick another row.
        void this.router.navigate(['/auth/customers']);
      });
  }

  /** Handler invoked by the shared form primitive on submit.
   * Public for test access (mirrors the team-create pattern). */
  onSubmit(data: CustomerFormData): void {
    const id = this.targetCustomerId();
    if (!id) return;
    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    // Build the PATCH body — personType and razonSocial are
    // intentionally NOT included (immutable per the backend
    // contract). Only include the per-branch fields that
    // actually belong to the current personType so we don't
    // PATCH-unset the wrong branch's fields on a swap (which
    // the backend would reject anyway, but cleaner here).
    const req: UpdateCustomerRequest = {
      firstName: data.firstName,
      lastName: data.lastName,
      email: data.email,
      dni: data.dni,
      cuitCuil: data.cuitCuil,
      dataConsent: data.dataConsent,
    };

    this.store
      .update(id, req)
      .then(() => {
        this.store.clearDetail();
        void this.router.navigate(['/auth/customers', id]);
      })
      .catch(() => {
        this.errorMessage.set(
          'No pudimos guardar los cambios. Revisá los datos e intentá de nuevo.',
        );
      })
      .finally(() => {
        this.isSubmitting.set(false);
      });
  }

  /** Cancel button handler — clears the cached detail and
   * navigates back to the detail page. */
  protected onCancel(): void {
    this.store.clearDetail();
    const id = this.targetCustomerId();
    if (!id) return;
    void this.router.navigate(['/auth/customers', id]);
  }
}
