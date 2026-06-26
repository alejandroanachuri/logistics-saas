import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { ShipmentPackage } from '../../core/types';

/**
 * Shape emitted by {@link PackageFormComponent} on submit.
 *
 * <p>Mirrors the per-package shape inside {@code CreateShipmentRequest.packages[]}
 * on the backend. Note {@code declaredCurrency} is left out — the
 * backend defaults server-side to {@code "ARS"} for domestic
 * shipments; the wizard sends the value only when the user
 * explicitly changes it (handled by the parent wizard, not
 * this form).
 */
export interface PackageFormData {
  weightKg: number;
  volumeCm3?: number;
  dimensionsCm?: string;
  contentDescription: string;
  declaredValue?: number;
  category:
    | 'GENERAL'
    | 'DOCUMENTOS'
    | 'ELECTRONICA'
    | 'ALIMENTOS'
    | 'MEDICAMENTOS'
    | 'PELIGROSO';
  isFragile: boolean;
  isUrgent: boolean;
  requiresSignature: boolean;
  requiresIdCheck: boolean;
}

interface PackageFormShape {
  weightKg: FormControl<number>;
  volumeCm3: FormControl<number | null>;
  dimensionsCm: FormControl<string>;
  contentDescription: FormControl<string>;
  declaredValue: FormControl<number | null>;
  category: FormControl<
    | 'GENERAL'
    | 'DOCUMENTOS'
    | 'ELECTRONICA'
    | 'ALIMENTOS'
    | 'MEDICAMENTOS'
    | 'PELIGROSO'
  >;
  isFragile: FormControl<boolean>;
  isUrgent: FormControl<boolean>;
  requiresSignature: FormControl<boolean>;
  requiresIdCheck: FormControl<boolean>;
}

const CATEGORIES: ReadonlyArray<{
  value:
    | 'GENERAL'
    | 'DOCUMENTOS'
    | 'ELECTRONICA'
    | 'ALIMENTOS'
    | 'MEDICAMENTOS'
    | 'PELIGROSO';
  label: string;
}> = [
  { value: 'GENERAL', label: 'General' },
  { value: 'DOCUMENTOS', label: 'Documentos' },
  { value: 'ELECTRONICA', label: 'Electrónica' },
  { value: 'ALIMENTOS', label: 'Alimentos' },
  { value: 'MEDICAMENTOS', label: 'Medicamentos' },
  { value: 'PELIGROSO', label: 'Mercadería peligrosa' },
];

/**
 * PackageForm — reactive form for a single {@link ShipmentPackage}.
 *
 * <p>Used by the shipment wizard (one form per package row
 * in a FormArray of packages). Captures physical + commercial
 * attributes — weight, dimensions, content, declared value,
 * category, and the fragile/urgent/signature/ID-check flags.
 *
 * <p>When a {@link ShipmentPackage} is passed via the
 * {@code pkg} input (edit mode), the form pre-fills with the
 * existing values. When null (create mode), defaults to an
 * empty form.
 *
 * <p>Standalone, OnPush, signal-input / output API.
 *
 * Usage:
 *   <app-package-form (submit)="onPkgSubmit($event)" />
 *   <app-package-form [pkg]="existing" (submit)="onPkgSubmit($event)" />
 */
@Component({
  selector: 'app-package-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="onSubmit()"
      class="space-y-4"
      data-package-form
      novalidate
    >
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Peso (kg) <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="number"
            step="0.1"
            min="0.1"
            formControlName="weightKg"
            data-field="weightKg"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">Volumen (cm³)</span>
          <input
            type="number"
            min="0"
            formControlName="volumeCm3"
            data-field="volumeCm3"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Dimensiones (cm)
          </span>
          <input
            type="text"
            formControlName="dimensionsCm"
            data-field="dimensionsCm"
            placeholder="Ej. 30x20x15"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
      </div>

      <label class="block space-y-1.5">
        <span class="block text-sm font-medium text-on-surface">
          Contenido <span class="text-error" aria-hidden="true">*</span>
        </span>
        <input
          type="text"
          formControlName="contentDescription"
          data-field="contentDescription"
          class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
        />
      </label>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">Valor declarado</span>
          <input
            type="number"
            min="0"
            formControlName="declaredValue"
            data-field="declaredValue"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">Categoría</span>
          <select
            formControlName="category"
            data-field="category"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          >
            @for (opt of categories; track opt.value) {
              <option [value]="opt.value">{{ opt.label }}</option>
            }
          </select>
        </label>
      </div>

      <fieldset class="grid grid-cols-1 gap-2 sm:grid-cols-2">
        <legend class="sr-only">Flags del paquete</legend>
        <label class="inline-flex items-center gap-2 text-sm text-on-surface">
          <input type="checkbox" formControlName="isFragile" data-field="isFragile" />
          <span>Frágil</span>
        </label>
        <label class="inline-flex items-center gap-2 text-sm text-on-surface">
          <input type="checkbox" formControlName="isUrgent" data-field="isUrgent" />
          <span>Urgente</span>
        </label>
        <label class="inline-flex items-center gap-2 text-sm text-on-surface">
          <input
            type="checkbox"
            formControlName="requiresSignature"
            data-field="requiresSignature"
          />
          <span>Requiere firma</span>
        </label>
        <label class="inline-flex items-center gap-2 text-sm text-on-surface">
          <input type="checkbox" formControlName="requiresIdCheck" data-field="requiresIdCheck" />
          <span>Verificación de identidad</span>
        </label>
      </fieldset>

      <div class="flex justify-end gap-2 pt-2">
        <button
          type="submit"
          data-submit
          [disabled]="form.invalid"
          class="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:bg-primary-fixed-dim disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Guardar paquete
        </button>
      </div>
    </form>
  `,
})
export class PackageFormComponent {
  /** Optional existing package (edit mode). */
  readonly pkg = input<ShipmentPackage | null>(null);

  /** Emits the form data on submit. */
  readonly submit = output<PackageFormData>();

  protected readonly categories = CATEGORIES;

  protected readonly form: FormGroup<PackageFormShape>;

  constructor(private readonly fb: FormBuilder) {
    this.form = this.fb.nonNullable.group<PackageFormShape>({
      weightKg: this.fb.nonNullable.control<number>(0, {
        validators: [Validators.required, Validators.min(0.1)],
      }),
      volumeCm3: this.fb.control<number | null>(null, {
        validators: [Validators.min(0)],
      }),
      dimensionsCm: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(20)],
      }),
      contentDescription: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.minLength(2), Validators.maxLength(200)],
      }),
      declaredValue: this.fb.control<number | null>(null, {
        validators: [Validators.min(0)],
      }),
      category: this.fb.nonNullable.control<
        | 'GENERAL'
        | 'DOCUMENTOS'
        | 'ELECTRONICA'
        | 'ALIMENTOS'
        | 'MEDICAMENTOS'
        | 'PELIGROSO'
      >('GENERAL', { validators: [Validators.required] }),
      isFragile: this.fb.nonNullable.control<boolean>(false),
      isUrgent: this.fb.nonNullable.control<boolean>(false),
      requiresSignature: this.fb.nonNullable.control<boolean>(false),
      requiresIdCheck: this.fb.nonNullable.control<boolean>(false),
    });

    effect(() => {
      const existing = this.pkg();
      if (!existing) return;
      this.form.patchValue({
        weightKg: existing.weightKg,
        volumeCm3: existing.volumeCm3,
        dimensionsCm: existing.dimensionsCm ?? '',
        contentDescription: existing.contentDescription,
        declaredValue: existing.declaredValue,
        category: existing.category,
        isFragile: existing.isFragile,
        isUrgent: existing.isUrgent,
        requiresSignature: existing.requiresSignature,
        requiresIdCheck: existing.requiresIdCheck,
      });
    });
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const data: PackageFormData = {
      weightKg: v.weightKg,
      volumeCm3: v.volumeCm3 ?? undefined,
      dimensionsCm: v.dimensionsCm || undefined,
      contentDescription: v.contentDescription,
      declaredValue: v.declaredValue ?? undefined,
      category: v.category,
      isFragile: v.isFragile,
      isUrgent: v.isUrgent,
      requiresSignature: v.requiresSignature,
      requiresIdCheck: v.requiresIdCheck,
    };
    this.submit.emit(data);
  }
}
