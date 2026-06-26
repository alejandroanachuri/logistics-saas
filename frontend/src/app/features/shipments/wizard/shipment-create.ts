import {
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  inject,
  signal,
  OnInit,
} from '@angular/core';
import { Router } from '@angular/router';

import { ShipmentsStore } from '../../../core/state/shipments-store';
import { CreateShipmentRequest } from '../../../core/types';
import { ShipmentCreateStep1CustomersComponent } from './shipment-create-step-1-customers';
import { ShipmentCreateStep2PackagesComponent } from './shipment-create-step-2-packages';
import { ShipmentCreateStep3ConfirmComponent } from './shipment-create-step-3-confirm';

/**
 * The {@code /auth/shipments/new} 3-step create wizard
 * (etapa-3-envios PR-7 Chunk B).
 *
 * <p>Mirrors the {@code /register} wizard pattern (etapa-1 F1)
 * but for shipments:
 * <ol>
 *   <li>**Step 1 — Customers:** pick the sender + receiver
 *       (different ids required).</li>
 *   <li>**Step 2 — Packages:** add 1+ package forms.</li>
 *   <li>**Step 3 — Confirm:** read-only summary + optional
 *       fields + create button.</li>
 * </ol>
 *
 * <p>The orchestrator holds the wizard state (current step,
 * submitting flag, error message) and reaches into the step
 * components via {@code @ViewChild} for {@code canAdvance} /
 * data extraction — same convention as the F1 register
 * wizard.
 *
 * <p>On a successful create the orchestrator navigates to
 * {@code /auth/shipments/:id} (the detail page); on cancel
 * it resets the wizard state and bounces back to the list.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-shipment-create',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ShipmentCreateStep1CustomersComponent,
    ShipmentCreateStep2PackagesComponent,
    ShipmentCreateStep3ConfirmComponent,
  ],
  templateUrl: './shipment-create.html',
})
export class ShipmentCreateComponent implements OnInit {
  @ViewChild(ShipmentCreateStep1CustomersComponent)
  readonly customersStep?: ShipmentCreateStep1CustomersComponent;
  @ViewChild(ShipmentCreateStep2PackagesComponent)
  readonly packagesStep?: ShipmentCreateStep2PackagesComponent;
  @ViewChild(ShipmentCreateStep3ConfirmComponent)
  readonly confirmStep?: ShipmentCreateStep3ConfirmComponent;

  readonly currentStepIndex = signal<0 | 1 | 2>(0);
  readonly isSubmitting = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  private readonly store = inject(ShipmentsStore);
  private readonly router = inject(Router);

  /** Per-step validity — read in the template's [disabled]
   * binding on the "Siguiente" button and by {@link next} /
   * {@link submit} to gate navigation. NOT a computed signal
   * because the per-step validity lives inside child
   * computed signals — a parent's computed reading them
   * would only re-run when the parent's own tracked signals
   * change, which would never trigger on a child signal
   * update. A plain method always re-evaluates and is cheap
   * to call from templates (OnPush re-reads on every CD). */
  canAdvance(): boolean {
    switch (this.currentStepIndex()) {
      case 0:
        return this.customersStep?.canAdvance() === true;
      case 1:
        return this.packagesStep?.canAdvance() === true;
      case 2:
        return this.confirmStep?.canSubmit() === true;
      default:
        return false;
    }
  }

  ngOnInit(): void {
    // Load the catalogs (branches + service levels). Future
    // chunks will wire these into the wizard's step 3
    // branches / service-level picker; for this chunk the
    // catalog loading is opportunistic and the wizard does
    // not depend on the result.
    void this.store.loadCatalogs().catch(() => {
      // Catalog failure is non-fatal — the wizard still
      // renders with empty catalogs. The store keeps its
      // previous value per contract.
    });
    // Reset the wizard state on mount so a stale draft from
    // a previous visit doesn't leak in.
    this.store.wizardReset();
  }

  next(): void {
    if (!this.canAdvance()) return;
    if (this.currentStepIndex() === 0) {
      // Advancing to step 2 — pre-fill the confirm step with
      // the customer picks + package drafts.
      const customers = this.customersStep;
      const packages = this.packagesStep;
      const confirm = this.confirmStep;
      if (customers?.senderSelection() && customers?.receiverSelection() && packages && confirm) {
        confirm.setSummary(
          customers.senderSelection()!,
          customers.receiverSelection()!,
          packages.packages(),
        );
      }
      this.currentStepIndex.set(1);
    } else if (this.currentStepIndex() === 1) {
      // Advancing to step 3 — refresh the summary in case
      // the user tweaked the package list since the last
      // call.
      const customers = this.customersStep;
      const packages = this.packagesStep;
      const confirm = this.confirmStep;
      if (customers?.senderSelection() && customers?.receiverSelection() && packages && confirm) {
        confirm.setSummary(
          customers.senderSelection()!,
          customers.receiverSelection()!,
          packages.packages(),
        );
      }
      this.currentStepIndex.set(2);
    }
  }

  previous(): void {
    if (this.currentStepIndex() > 0) {
      this.currentStepIndex.set((this.currentStepIndex() - 1) as 0 | 1 | 2);
    }
  }

  /** Submit handler — wired from the confirm step's
   * {@code (confirmed)} output. Builds the
   * {@link CreateShipmentRequest}, hands it to the store,
   * and navigates on success. */
  onConfirmed(payload: CreateShipmentRequest): void {
    if (this.isSubmitting()) return;
    this.isSubmitting.set(true);
    this.errorMessage.set(null);
    // Also flag the confirm step so it can disable its own
    // button.
    this.confirmStep?.isSubmitting.set(true);

    // Push the payload into the store's wizard draft. The
    // store's wizardSubmit method will then read it back.
    this.store.wizardPatchData(payload);

    this.store
      .wizardSubmit()
      .then((resp) => {
        void this.router.navigate(['/auth/shipments', resp.shipment.id]);
      })
      .catch(() => {
        this.errorMessage.set('No pudimos crear el envío. Revisá los datos e intentá de nuevo.');
        this.isSubmitting.set(false);
        this.confirmStep?.isSubmitting.set(false);
      });
  }

  /** Cancel — resets the wizard state and bounces to the
   * shipments list. Wired to the top-of-page "Cancelar"
   * link. */
  cancel(): void {
    this.store.wizardReset();
    void this.router.navigate(['/auth/shipments']);
  }

  /** Submit wrapper used by the orchestrator's own
   * "Crear envío" button. Delegates to the confirm step's
   * internal {@code confirm()} which builds the payload and
   * emits via the {@code confirmed} output (which in turn
   * calls {@link onConfirmed}). Exposed for tests + as a
   * stable handle on the orchestrator for the template. */
  submit(): void {
    this.confirmStep?.confirm();
  }
}
