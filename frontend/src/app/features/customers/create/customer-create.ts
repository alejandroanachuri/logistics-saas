import {
  ChangeDetectionStrategy,
  Component,
  inject,
  signal,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { CustomersStore } from '../../../core/state/customers-store';
import {
  CustomerFormComponent,
  CustomerFormData,
} from '../../../shared/ui/customer-form';
import { CreateCustomerRequest } from '../../../core/types';

/**
 * Customer create page (`/auth/customers/new`) — etapa-3-envios
 * PR-6 Chunk A.
 *
 * <p>Thin wrapper around the shared {@code CustomerFormComponent}
 * primitive (PR-5). The form primitive owns the FISICA / JURIDICA
 * branching, the per-branch validators, and the consent checkbox;
 * this page just owns:
 * - the section heading + cancel link
 * - the submit handler that calls {@code customers-store.create()}
 * - the post-submit navigation back to the list
 * - the error message fallback when the API rejects
 *
 * <p>Address is out of scope for this chunk (it lives on its own
 * entity). After a customer record exists, addresses are managed
 * from a future page or directly via the API.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-customer-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CustomerFormComponent],
  templateUrl: './customer-create.html',
})
export class CustomerCreateComponent {
  private readonly router = inject(Router);
  private readonly customersStore = inject(CustomersStore);

  /** Local UI state. */
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  /** Handler invoked by the shared form primitive on submit.
   * Public for test access (mirrors the team-create pattern). */
  onSubmit(data: CustomerFormData): void {
    this.isSubmitting.set(true);
    this.errorMessage.set(null);

    const req: CreateCustomerRequest = {
      personType: data.personType,
      firstName: data.firstName,
      lastName: data.lastName,
      razonSocial: data.razonSocial,
      dni: data.dni,
      cuitCuil: data.cuitCuil,
      taxCondition: data.taxCondition,
      phone: data.phone,
      email: data.email,
      dataConsent: data.dataConsent,
    };

    this.customersStore
      .create(req)
      .then(() => {
        void this.router.navigate(['/auth/customers']);
      })
      .catch(() => {
        this.errorMessage.set(
          'No pudimos crear el cliente. Revisá los datos e intentá de nuevo.',
        );
      })
      .finally(() => {
        this.isSubmitting.set(false);
      });
  }

  /** Cancel button handler — navigates back to the list page. */
  protected onCancel(): void {
    void this.router.navigate(['/auth/customers']);
  }
}
