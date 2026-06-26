import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, input, output } from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import { Address } from '../../core/types';

/**
 * Shape emitted by {@link AddressFormComponent} on submit.
 *
 * <p>Mirrors {@code CreateAddressRequest} on the backend:
 * the wire contract requires {@code street}, {@code number},
 * {@code city}, {@code province}, {@code postalCode}, and
 * {@code country}; the others ({@code floor}, {@code apartment},
 * {@code reference}) are optional and nullable.
 */
export interface AddressFormData {
  street: string;
  number: string;
  floor?: string;
  apartment?: string;
  city: string;
  province: string;
  postalCode: string;
  reference?: string;
  country: string;
}

interface AddressFormShape {
  street: FormControl<string>;
  number: FormControl<string>;
  floor: FormControl<string>;
  apartment: FormControl<string>;
  city: FormControl<string>;
  province: FormControl<string>;
  postalCode: FormControl<string>;
  reference: FormControl<string>;
  country: FormControl<string>;
}

/**
 * AddressForm — reactive form for creating or editing an address.
 *
 * <p>Required fields per the backend contract: street, number,
 * city, province, postalCode, country. Optional: floor, apartment,
 * reference (Argentina reality: high-rise buildings commonly
 * need floor + apartment; houses / small towns never do).
 *
 * <p>The component is purely presentational + stateful — the
 * parent owns the API call and navigation.
 *
 * <p>Standalone, OnPush, signal-input / output API.
 *
 * Usage:
 *   <app-address-form (submit)="onSubmit($event)" />
 *   <app-address-form [address]="existing" (submit)="onSubmit($event)" />
 */
@Component({
  selector: 'app-address-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="onSubmit()"
      class="space-y-4"
      data-address-form
      novalidate
    >
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <label class="block space-y-1.5 sm:col-span-2">
          <span class="block text-sm font-medium text-on-surface">
            Calle <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="street"
            data-field="street"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Número <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="number"
            data-field="number"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">Piso</span>
          <input
            type="text"
            formControlName="floor"
            data-field="floor"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">Departamento</span>
          <input
            type="text"
            formControlName="apartment"
            data-field="apartment"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
        <label class="block space-y-1.5 sm:col-span-2">
          <span class="block text-sm font-medium text-on-surface">
            Ciudad <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="city"
            data-field="city"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Provincia <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="province"
            data-field="province"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
      </div>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Código postal <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="postalCode"
            data-field="postalCode"
            inputmode="numeric"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            País <span class="text-error" aria-hidden="true">*</span>
          </span>
          <input
            type="text"
            formControlName="country"
            data-field="country"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
        </label>
      </div>

      <label class="block space-y-1.5">
        <span class="block text-sm font-medium text-on-surface">Referencia</span>
        <textarea
          rows="2"
          formControlName="reference"
          data-field="reference"
          class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
        ></textarea>
      </label>

      <div class="flex justify-end gap-2 pt-2">
        <button
          type="submit"
          data-submit
          [disabled]="form.invalid"
          class="inline-flex items-center rounded-md bg-primary px-4 py-2 text-sm font-semibold text-on-primary hover:bg-primary-fixed-dim disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Guardar
        </button>
      </div>
    </form>
  `,
})
export class AddressFormComponent {
  readonly address = input<Address | null>(null);

  readonly submit = output<AddressFormData>();

  protected readonly form: FormGroup<AddressFormShape>;

  constructor(private readonly fb: FormBuilder) {
    this.form = this.fb.nonNullable.group<AddressFormShape>({
      street: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.maxLength(120)],
      }),
      number: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.maxLength(10)],
      }),
      floor: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(10)],
      }),
      apartment: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(10)],
      }),
      city: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.maxLength(80)],
      }),
      province: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.maxLength(80)],
      }),
      postalCode: this.fb.nonNullable.control<string>('', {
        validators: [Validators.required, Validators.maxLength(10)],
      }),
      reference: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(200)],
      }),
      country: this.fb.nonNullable.control<string>('AR', {
        validators: [Validators.required, Validators.maxLength(2)],
      }),
    });

    effect(() => {
      const existing = this.address();
      if (!existing) return;
      this.form.patchValue({
        street: existing.street ?? '',
        number: existing.number ?? '',
        floor: existing.floor ?? '',
        apartment: existing.apartment ?? '',
        city: existing.city ?? '',
        province: existing.province ?? '',
        postalCode: existing.postalCode ?? '',
        reference: existing.reference ?? '',
        country: existing.country ?? 'AR',
      });
    });
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const data: AddressFormData = {
      street: v.street,
      number: v.number,
      floor: v.floor || undefined,
      apartment: v.apartment || undefined,
      city: v.city,
      province: v.province,
      postalCode: v.postalCode,
      reference: v.reference || undefined,
      country: v.country,
    };
    this.submit.emit(data);
  }
}
