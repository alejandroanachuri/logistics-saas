import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { CustomersStore } from '../../../core/state/customers-store';
import { CustomersService } from '../../../core/services/customers.service';
import {
  CustomerFormComponent,
  CustomerFormData,
} from '../../../shared/ui/customer-form';
import { CreateCustomerRequest } from '../../../core/types';

/**
 * Customer create page (`/auth/customers/new`) — etapa-3-envios
 * PR-6 Chunk A, with the wizard-round-trip enhancement.
 *
 * <p>Thin wrapper around the shared {@code CustomerFormComponent}
 * primitive (PR-5). The form primitive owns the FISICA / JURIDICA
 * branching, the per-branch validators, and the consent checkbox;
 * this page just owns:
 * - the section heading + cancel link
 * - the submit handler that calls {@code customers-store.create()}
 * - the post-submit navigation back to the list (or the wizard, if
 *   the operator came from the wizard via the inline "Crear nuevo"
 *   CTA in step 1)
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
  imports: [CustomerFormComponent],
  templateUrl: './customer-create.html',
})
export class CustomerCreateComponent {
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly customersStore = inject(CustomersStore);
  private readonly customersService = inject(CustomersService);

  /** Captured at construction: where the wizard sends us back to. */
  protected readonly returnTo = signal<string | null>(
    this.route.snapshot.queryParamMap.get('returnTo'),
  );
  /** Captured at construction: which side we're pre-selecting. */
  protected readonly preSelectedRole = signal<'sender' | 'receiver' | null>(
    (() => {
      const r = this.route.snapshot.queryParamMap.get('role');
      return r === 'sender' || r === 'receiver' ? r : null;
    })(),
  );
  /** True when the operator came here from the wizard — relaxes
   * the form (phone + dataConsent optional, tax condition default). */
  protected readonly minimumViable = computed<boolean>(() => this.returnTo() !== null);

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
        // After create: re-fetch the list to capture the new id
        // (the store doesn't expose the new id from .create()), then
        // either navigate back to the wizard (with preSelected query
        // param) or to the customer list.
        const returnTo = this.returnTo();
        const role = this.preSelectedRole();
        if (returnTo && role) {
          // Search by DNI/CUIT (most unique identifier) to find the
          // just-created customer. Falls back to no search if the
          // customer has neither.
          const search = req.dni ?? req.cuitCuil ?? '';
          firstValueFrom(
            this.customersService.list({ page: 1, size: 10, search }),
          ).then((res) => {
            // The just-created customer is the one with the highest
            // createdAt. If the search by DNI matched, take that one;
            // otherwise pick the latest by createdAt.
            const fresh = (res.data ?? [])
              .slice()
              .sort((a, b) => (b.createdAt ?? '').localeCompare(a.createdAt ?? ''))[0];
            if (fresh) {
              void this.router.navigate([returnTo], {
                queryParams: {
                  preSelected: fresh.id,
                  role,
                },
              });
              return;
            }
            void this.router.navigateByUrl(returnTo);
          });
        } else {
          void this.router.navigate(['/auth/customers']);
        }
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

  /** Cancel button handler — navigates back to the list page (or
   * the wizard if returnTo is set). */
  protected onCancel(): void {
    const returnTo = this.returnTo();
    if (returnTo) {
      void this.router.navigateByUrl(returnTo);
    } else {
      void this.router.navigate(['/auth/customers']);
    }
  }
}
