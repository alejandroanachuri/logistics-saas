import { ChangeDetectionStrategy, Component, computed, output, signal } from '@angular/core';

import { CreateShipmentRequest } from '../../../core/types';
import { CustomerSummary } from '../../../core/types';
import { PackageDraft } from './shipment-create-step-2-packages';

/**
 * Step 3 of the shipment-create wizard (etapa-3-envios PR-7 Chunk B).
 *
 * <p>Read-only summary of the wizard state plus the optional
 * delivery-level fields. The parent
 * {@code ShipmentCreateComponent} calls
 * {@link setSummary} when the user advances from step 2.
 *
 * <p>Fields exposed on the summary:
 * <ul>
 *   <li>Sender + receiver (display name from the customer record)</li>
 *   <li>Package count + total weight</li>
 *   <li>Per-package table (weight + content + category + flags)</li>
 * </ul>
 *
 * <p>Optional fields (collected here, on step 3, since they
 * belong with the "review" step):
 * <ul>
 *   <li>{@code deliveryAddressId} (required by the backend —
 *       the wizard surfaces a free-form input here; production
 *       will wire it to an address picker in a future chunk)</li>
 *   <li>{@code deliveryInstructions}</li>
 *   <li>{@code promisedDeliveryDate}</li>
 *   <li>{@code validateNow} — when true, the backend runs the
 *       FSM validate pass inline</li>
 * </ul>
 *
 * <p>{@link confirm} emits the assembled
 * {@link CreateShipmentRequest} via the {@link confirmed}
 * output. The parent owns the actual store call.
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-shipment-create-step-3-confirm',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shipment-create-step-3-confirm.html',
})
export class ShipmentCreateStep3ConfirmComponent {
  /** Sender selection — set by {@link setSummary}. */
  readonly sender = signal<CustomerSummary | null>(null);

  /** Receiver selection — set by {@link setSummary}. */
  readonly receiver = signal<CustomerSummary | null>(null);

  /** Package drafts — set by {@link setSummary}. */
  readonly packages = signal<PackageDraft[]>([]);

  /** Total weight (kg) across all packages. */
  readonly totalWeight = signal<number>(0);

  /** Optional: delivery address id. Required by the backend
   * on POST. The wizard surfaces a free-form input here; a
   * future chunk will replace it with a proper picker. */
  readonly deliveryAddressId = signal<string>('');

  /** Optional: free-form delivery instructions. */
  readonly deliveryInstructions = signal<string>('');

  /** Optional: ISO-8601 date string for the promised delivery
   * date. */
  readonly promisedDeliveryDate = signal<string>('');

  /** Optional: when true, the backend runs the FSM validate
   * pass inline; when false (default), the shipment is created
   * in PRE_ALTA. */
  readonly validateNow = signal<boolean>(false);

  /** Submitting flag — set by the parent while the create
   * POST is in flight. Disables the "Crear envío" button to
   * prevent double-submits. */
  readonly isSubmitting = signal<boolean>(false);

  /** Mirrors {@link isSubmitting} for the template. */
  readonly isLoading = computed<boolean>(() => this.isSubmitting());

  /** True when the user can click "Crear envío": address id
   * is set AND not currently submitting. The send/receive
   * uniqueness and the package validity are already enforced
   * by the prior steps, so we don't re-check here. */
  readonly canSubmit = computed<boolean>(
    () => this.deliveryAddressId().trim().length > 0 && !this.isSubmitting(),
  );

  /** Emits the assembled create-request payload on submit. */
  readonly confirmed = output<CreateShipmentRequest>();

  /** Inject the summary state from the parent. Called by the
   * wizard when the user advances from step 2. */
  setSummary(sender: CustomerSummary, receiver: CustomerSummary, packages: PackageDraft[]): void {
    this.sender.set(sender);
    this.receiver.set(receiver);
    this.packages.set(packages);
    this.totalWeight.set(packages.reduce((sum, p) => sum + (p.weightKg || 0), 0));
  }

  /** Build and emit the create payload. No-op when the
   * deliveryAddressId is missing or a submit is already
   * in flight. */
  confirm(): void {
    if (!this.canSubmit()) return;
    const sender = this.sender();
    const receiver = this.receiver();
    if (!sender || !receiver) return;

    const payload: CreateShipmentRequest = {
      senderId: sender.id,
      receiverId: receiver.id,
      deliveryAddressId: this.deliveryAddressId().trim(),
      paymentType: 'PAGO_ORIGEN',
      packages: this.packages().map((p) => ({
        weightKg: p.weightKg,
        volumeCm3: p.volumeCm3 ?? undefined,
        dimensionsCm: p.dimensionsCm || undefined,
        contentDescription: p.contentDescription,
        declaredValue: p.declaredValue ?? undefined,
        category: p.category,
        isFragile: p.isFragile,
        isUrgent: p.isUrgent,
        requiresSignature: p.requiresSignature,
        requiresIdCheck: p.requiresIdCheck,
      })),
      deliveryInstructions: this.deliveryInstructions().trim() || undefined,
      promisedDeliveryDate: this.promisedDeliveryDate().trim() || undefined,
      validateNow: this.validateNow(),
    };
    this.confirmed.emit(payload);
  }

  /** Display-friendly customer name — razonSocial for JURIDICA,
   * "First Last" for FISICA. Mirrors the picker step helper. */
  displayName(row: CustomerSummary): string {
    if (row.razonSocial) return row.razonSocial;
    const f = (row.firstName ?? '').trim();
    const l = (row.lastName ?? '').trim();
    if (f && l) return `${f} ${l}`;
    if (f) return f;
    if (l) return l;
    return '—';
  }

  /** Spanish label for the category discriminator. */
  protected displayCategory(
    category: 'GENERAL' | 'DOCUMENTOS' | 'ELECTRONICA' | 'ALIMENTOS' | 'MEDICAMENTOS' | 'PELIGROSO',
  ): string {
    switch (category) {
      case 'GENERAL':
        return 'General';
      case 'DOCUMENTOS':
        return 'Documentos';
      case 'ELECTRONICA':
        return 'Electrónica';
      case 'ALIMENTOS':
        return 'Alimentos';
      case 'MEDICAMENTOS':
        return 'Medicamentos';
      case 'PELIGROSO':
        return 'Mercadería peligrosa';
      default:
        return category;
    }
  }
}
