import { ChangeDetectionStrategy, Component, computed, signal } from '@angular/core';
import { PackageFormComponent, PackageFormData } from '../../../shared/ui/package-form';

/** A single package slot in the wizard. Mirrors the
 * {@code CreateShipmentRequest.packages[]} entry shape on the
 * backend, with default values for the always-present fields. */
export interface PackageDraft {
  weightKg: number;
  volumeCm3: number | null;
  dimensionsCm: string;
  contentDescription: string;
  declaredValue: number | null;
  category: 'GENERAL' | 'DOCUMENTOS' | 'ELECTRONICA' | 'ALIMENTOS' | 'MEDICAMENTOS' | 'PELIGROSO';
  isFragile: boolean;
  isUrgent: boolean;
  requiresSignature: boolean;
  requiresIdCheck: boolean;
}

function emptyDraft(): PackageDraft {
  return {
    weightKg: 0,
    volumeCm3: null,
    dimensionsCm: '',
    contentDescription: '',
    declaredValue: null,
    category: 'GENERAL',
    isFragile: false,
    isUrgent: false,
    requiresSignature: false,
    requiresIdCheck: false,
  };
}

/**
 * Step 2 of the shipment-create wizard (etapa-3-envios PR-7 Chunk B).
 *
 * <p>Maintains a {@link packages} signal holding the per-package
 * drafts. Each draft renders through the shared
 * {@link PackageFormComponent} primitive (PR-5). The user can
 * add more packages ("Agregar otro paquete") or remove any
 * non-final one.
 *
 * <p>{@link canAdvance} gates the wizard's "Siguiente" button:
 * true only when there's at least one package AND every
 * package has {@code weightKg > 0} and a non-empty
 * {@code contentDescription}.
 *
 * <p>Standalone, OnPush, signal-first. The parent
 * {@code ShipmentCreateComponent} reads {@link packages} via
 * {@code @ViewChild} when the user clicks Siguiente.
 */
@Component({
  selector: 'app-shipment-create-step-2-packages',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [PackageFormComponent],
  templateUrl: './shipment-create-step-2-packages.html',
})
export class ShipmentCreateStep2PackagesComponent {
  /** The wizard's package drafts. Starts with one slot so the
   * user can fill it in immediately; additional slots are
   * appended via {@link addPackage}. */
  readonly packages = signal<PackageDraft[]>([emptyDraft()]);

  /** True when the user can advance past step 2: at least one
   * package with valid weight + content. */
  readonly canAdvance = computed<boolean>(() => {
    const list = this.packages();
    if (list.length === 0) return false;
    return list.every((p) => p.weightKg > 0 && p.contentDescription.trim().length > 0);
  });

  /** Sum of {@code weightKg} across all packages — exposed for
   * the parent wizard + the confirm step's summary. */
  readonly totalWeightKg = computed<number>(() =>
    this.packages().reduce((sum, p) => sum + (p.weightKg || 0), 0),
  );

  /** Append a new empty package slot. */
  addPackage(): void {
    this.packages.update((list) => [...list, emptyDraft()]);
  }

  /** Remove the package at the given index. No-op when the
   * index is out of range or when it would leave the list
   * empty (the wizard always requires at least one package). */
  removePackage(index: number): void {
    const list = this.packages();
    if (index < 0 || index >= list.length) return;
    if (list.length === 1) return; // keep at least one
    const next = list.filter((_, i) => i !== index);
    this.packages.set(next);
  }

  /** Merge a {@link PackageFormData} payload into the slot at
   * the given index. Wired from each {@link PackageFormComponent}'s
   * {@code submit} output. */
  updatePackage(index: number, data: PackageFormData): void {
    const list = this.packages();
    if (index < 0 || index >= list.length) return;
    const next = list.map((p, i) =>
      i === index
        ? {
            weightKg: data.weightKg,
            volumeCm3: data.volumeCm3 ?? null,
            dimensionsCm: data.dimensionsCm ?? '',
            contentDescription: data.contentDescription,
            declaredValue: data.declaredValue ?? null,
            category: data.category,
            isFragile: data.isFragile,
            isUrgent: data.isUrgent,
            requiresSignature: data.requiresSignature,
            requiresIdCheck: data.requiresIdCheck,
          }
        : p,
    );
    this.packages.set(next);
  }
}
