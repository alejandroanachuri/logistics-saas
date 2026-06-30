import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, input, output } from '@angular/core';
import {
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';

import {
  Customer,
  CustomerPersonType,
  CustomerTaxCondition,
} from '../../core/types';

/**
 * Shape emitted by {@link CustomerFormComponent} on submit.
 *
 * <p>Mirrors the {@code CreateCustomerRequest} / {@code UpdateCustomerRequest}
 * wire types: the {@code personType} discriminator selects between
 * the FISICA and JURIDICA branches; the receiver picks one path
 * and the other branch's fields are dropped on submit.
 *
 * <p><b>Minimum-viable mode</b> (added in etapa-3 follow-up): the
 * caller can set {@link CustomerFormData.minimumViable} to {@code true}
 * to relax the validators — phone becomes optional, the dataConsent
 * checkbox becomes optional (defaults to false), and the
 * taxCondition defaults to {@code NO_CATEGORIZADO}. This is the
 * mode used by the "create from wizard" flow, where the operator
 * often has only a name and a phone (or just a name) for a
 * recipient. The backend will accept the relaxed payload (the
 * server-side validators allow optional phone + NO_CATEGORIZADO
 * for tax condition).
 */
export interface CustomerFormData {
  personType: CustomerPersonType;
  firstName?: string;
  lastName?: string;
  razonSocial?: string;
  dni?: string;
  cuitCuil?: string;
  taxCondition: CustomerTaxCondition;
  phone: string;
  email?: string;
  dataConsent: boolean;

  /**
   * If true, the form relaxed the validators (phone optional,
   * dataConsent optional, taxCondition default to NO_CATEGORIZADO)
   * to support a quick-create flow from the shipment wizard. The
   * backend will accept the relaxed payload.
   */
  minimumViable?: boolean;
}

interface CustomerFormShape {
  personType: FormControl<CustomerPersonType>;
  firstName: FormControl<string>;
  lastName: FormControl<string>;
  razonSocial: FormControl<string>;
  dni: FormControl<string>;
  cuitCuil: FormControl<string>;
  taxCondition: FormControl<CustomerTaxCondition>;
  phone: FormControl<string>;
  email: FormControl<string>;
  dataConsent: FormControl<boolean>;
}

const TAX_CONDITIONS: ReadonlyArray<{ value: CustomerTaxCondition; label: string }> = [
  { value: 'RESPONSABLE_INSCRIPTO', label: 'Responsable inscripto' },
  { value: 'MONOTRIBUTISTA', label: 'Monotributista' },
  { value: 'EXENTO', label: 'Exento' },
  { value: 'CONSUMIDOR_FINAL', label: 'Consumidor final' },
  { value: 'NO_CATEGORIZADO', label: 'No categorizado' },
];

/**
 * CustomerForm — reactive form for creating or editing a customer.
 *
 * <p>Supports the {@code FISICA} (DNI) and {@code JURIDICA} (CUIT)
 * branches via a {@code personType} radio control. The branch's
 * non-applicable controls are disabled while the active branch's
 * validators are tightened.
 *
 * <p>When a {@link Customer} is passed via the {@code customer}
 * input (edit mode), the form pre-fills with the existing values.
 * When null (create mode), defaults to FISICA + empty values.
 *
 * <p>On submit, the form emits a {@link CustomerFormData} via the
 * {@code submit} output. The parent owns the API call and
 * navigation — the component is presentational + stateful.
 *
 * <p>Standalone, OnPush, signal-input / output API. Uses Angular
 * Reactive Forms internally.
 *
 * Usage:
 *   <app-customer-form (submit)="onSubmit($event)" />
 *   <app-customer-form [customer]="existing" (submit)="onSubmit($event)" />
 */
@Component({
  selector: 'app-customer-form',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <form
      [formGroup]="form"
      (ngSubmit)="onSubmit()"
      class="space-y-4"
      data-customer-form
      novalidate
    >
      @if (minimumViable()) {
        <p
          role="status"
          class="rounded-md border border-tertiary-container bg-tertiary-container px-3 py-2 text-xs text-on-tertiary-container"
          data-minimum-viable-banner
        >
          Modo rápido: los campos marcados con <span class="text-error" aria-hidden="true">*</span> son obligatorios, el resto los podés completar después editando el cliente.
        </p>
      }

      <fieldset class="space-y-2">
        <legend class="text-sm font-medium text-on-surface">Tipo de persona</legend>
        <div class="flex gap-4">
          <label class="inline-flex items-center gap-2 text-sm text-on-surface">
            <input
              type="radio"
              formControlName="personType"
              value="FISICA"
              data-person-type="FISICA"
            />
            <span>Persona física</span>
          </label>
          <label class="inline-flex items-center gap-2 text-sm text-on-surface">
            <input
              type="radio"
              formControlName="personType"
              value="JURIDICA"
              data-person-type="JURIDICA"
            />
            <span>Persona jurídica</span>
          </label>
        </div>
      </fieldset>

      @if (isFisica()) {
        <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <label class="block space-y-1.5">
            <span class="block text-sm font-medium text-on-surface">Nombre</span>
            <input
              type="text"
              formControlName="firstName"
              data-field="firstName"
              class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="block text-sm font-medium text-on-surface">Apellido</span>
            <input
              type="text"
              formControlName="lastName"
              data-field="lastName"
              class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </label>
          <label class="block space-y-1.5 sm:col-span-2">
            <span class="block text-sm font-medium text-on-surface">
              DNI <span class="text-error" aria-hidden="true">*</span>
            </span>
            <input
              type="text"
              formControlName="dni"
              data-field="dni"
              inputmode="numeric"
              pattern="[0-9]{7,8}"
              class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
            @if (showFieldError('dni'); as err) {
              <p role="alert" class="text-sm text-error">{{ err }}</p>
            }
          </label>
        </div>
      } @else {
        <div class="space-y-4">
          <label class="block space-y-1.5">
            <span class="block text-sm font-medium text-on-surface">Razón social</span>
            <input
              type="text"
              formControlName="razonSocial"
              data-field="razonSocial"
              class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
          </label>
          <label class="block space-y-1.5">
            <span class="block text-sm font-medium text-on-surface">
              CUIT / CUIL <span class="text-error" aria-hidden="true">*</span>
            </span>
            <input
              type="text"
              formControlName="cuitCuil"
              data-field="cuitCuil"
              inputmode="numeric"
              pattern="[0-9]{11}"
              class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
            />
            @if (showFieldError('cuitCuil'); as err) {
              <p role="alert" class="text-sm text-error">{{ err }}</p>
            }
          </label>
        </div>
      }

      <label class="block space-y-1.5">
        <span class="block text-sm font-medium text-on-surface">
          Condición frente al IVA @if (minimumViable()) { <span class="text-xs text-on-surface-variant">(opcional — default "No categorizado")</span> }
        </span>
        <select
          formControlName="taxCondition"
          data-field="taxCondition"
          class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
        >
          @for (opt of taxConditions; track opt.value) {
            <option [value]="opt.value">{{ opt.label }}</option>
          }
        </select>
      </label>

      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Teléfono @if (!minimumViable()) { <span class="text-error" aria-hidden="true">*</span> }
            @if (minimumViable()) { <span class="text-xs text-on-surface-variant">(opcional)</span> }
          </span>
          <input
            type="tel"
            formControlName="phone"
            data-field="phone"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
          @if (showFieldError('phone'); as err) {
            <p role="alert" class="text-sm text-error">{{ err }}</p>
          }
        </label>
        <label class="block space-y-1.5">
          <span class="block text-sm font-medium text-on-surface">
            Email @if (minimumViable()) { <span class="text-xs text-on-surface-variant">(opcional)</span> }
          </span>
          <input
            type="email"
            formControlName="email"
            data-field="email"
            class="block w-full rounded-md border border-outline-variant bg-surface-container-lowest px-3 py-2 text-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
          />
          @if (showFieldError('email'); as err) {
            <p role="alert" class="text-sm text-error">{{ err }}</p>
          }
        </label>
      </div>

      <label class="flex items-start gap-2 text-sm text-on-surface">
        <input
          type="checkbox"
          formControlName="dataConsent"
          data-field="dataConsent"
          class="mt-0.5"
        />
        <span>
          Acepto el tratamiento de mis datos personales conforme a la Ley 25.326 (PDP).
          @if (!minimumViable()) { <span class="text-error" aria-hidden="true">*</span> }
          @if (minimumViable()) { <span class="text-xs text-on-surface-variant">(opcional en modo rápido)</span> }
        </span>
      </label>
      @if (showFieldError('dataConsent'); as err) {
        <p role="alert" class="text-sm text-error">{{ err }}</p>
      }

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
export class CustomerFormComponent {
  /** Optional existing customer (edit mode). When null the
   * form renders empty (create mode). */
  readonly customer = input<Customer | null>(null);

  /**
   * When true, the form relaxes validators for a quick-create
   * flow (used by the "create from wizard" CTA). Phone and
   * dataConsent become optional, taxCondition defaults to
   * NO_CATEGORIZADO. A small banner in the form explains the
   * mode to the operator. Backend accepts the relaxed payload.
   */
  readonly minimumViable = input<boolean>(false);

  /** Emits the form data on submit. The parent owns the
   * API call + navigation. */
  readonly submit = output<CustomerFormData>();

  protected readonly taxConditions = TAX_CONDITIONS;

  protected readonly form: FormGroup<CustomerFormShape>;

  protected readonly isFisica = computed(
    () => this.form.controls.personType.value === 'FISICA',
  );

  constructor(private readonly fb: FormBuilder) {
    this.form = this.fb.nonNullable.group<CustomerFormShape>({
      personType: this.fb.nonNullable.control<CustomerPersonType>('FISICA', {
        validators: [Validators.required],
      }),
      firstName: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(80)],
      }),
      lastName: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(80)],
      }),
      razonSocial: this.fb.nonNullable.control<string>('', {
        validators: [Validators.maxLength(120)],
      }),
      dni: this.fb.nonNullable.control<string>('', {
        validators: [Validators.pattern(/^[0-9]{7,8}$/)],
      }),
      cuitCuil: this.fb.nonNullable.control<string>('', {
        validators: [Validators.pattern(/^[0-9]{11}$/)],
      }),
      taxCondition: this.fb.nonNullable.control<CustomerTaxCondition>(
        'CONSUMIDOR_FINAL',
        { validators: [Validators.required] },
      ),
      phone: this.fb.nonNullable.control<string>('', {
        // Validator applied by the minimumViable effect below.
        validators: [Validators.minLength(6)],
      }),
      email: this.fb.nonNullable.control<string>('', {
        validators: [Validators.email],
      }),
      dataConsent: this.fb.nonNullable.control<boolean>(false, {
        // Validator applied by the minimumViable effect below.
        validators: [],
      }),
    });

    // Toggle the validators on the FISICA/JURIDICA branch
    // when the personType changes — only the active branch
    // is required + the complementary branch's validators
    // are cleared so the form reports a valid state.
    this.form.controls.personType.valueChanges.subscribe((value) => {
      if (value === 'FISICA') {
        this.form.controls.dni.setValidators([Validators.required, Validators.pattern(/^[0-9]{7,8}$/)]);
        this.form.controls.dni.updateValueAndValidity();
        this.form.controls.cuitCuil.clearValidators();
        this.form.controls.cuitCuil.updateValueAndValidity();
      } else {
        this.form.controls.cuitCuil.setValidators([
          Validators.required,
          Validators.pattern(/^[0-9]{11}$/),
        ]);
        this.form.controls.cuitCuil.updateValueAndValidity();
        this.form.controls.dni.clearValidators();
        this.form.controls.dni.updateValueAndValidity();
      }
    });

    // Apply minimum-viable mode: relax phone + dataConsent and
    // default taxCondition to NO_CATEGORIZADO. Runs whenever the
    // input flips (or stays the same — we set it on every effect
    // run because the validator set is stable).
    effect(() => {
      const mv = this.minimumViable();
      if (mv) {
        this.form.controls.phone.setValidators([Validators.minLength(6)]);
        this.form.controls.dataConsent.setValidators([]);
        this.form.controls.taxCondition.setValue('NO_CATEGORIZADO');
      } else {
        this.form.controls.phone.setValidators([Validators.required, Validators.minLength(6)]);
        this.form.controls.dataConsent.setValidators([Validators.requiredTrue]);
      }
      this.form.controls.phone.updateValueAndValidity();
      this.form.controls.dataConsent.updateValueAndValidity();
      this.form.controls.taxCondition.updateValueAndValidity();
    });

    // Pre-fill when the customer input is set (edit mode).
    // When minimumViable is true AND there's no pre-fill, skip
    // setting the taxCondition default to NO_CATEGORIZADO (the
    // effect above already set it).
    effect(() => {
      const existing = this.customer();
      if (!existing) return;
      this.form.patchValue({
        personType: existing.personType,
        firstName: existing.firstName ?? '',
        lastName: existing.lastName ?? '',
        razonSocial: existing.razonSocial ?? '',
        dni: existing.dni ?? '',
        cuitCuil: existing.cuitCuil ?? '',
        taxCondition: existing.taxCondition,
        phone: existing.phone,
        email: existing.email ?? '',
        dataConsent: existing.dataConsent,
      });
    });
  }

  /**
   * Show the error message for a field only when the user
   * has interacted with it (dirty or touched) AND the field
   * is invalid. Returns the first matching error key.
   */
  protected showFieldError(field: keyof CustomerFormShape): string | null {
    const control = this.form.controls[field];
    if (!control || !(control.dirty || control.touched) || control.valid) {
      return null;
    }
    if (control.hasError('required') || control.hasError('requiredTrue')) {
      return 'Este campo es obligatorio.';
    }
    if (control.hasError('email')) {
      return 'Email inválido.';
    }
    if (control.hasError('pattern')) {
      if (field === 'dni') return 'DNI inválido (7 u 8 dígitos).';
      if (field === 'cuitCuil') return 'CUIT/CUIL inválido (11 dígitos).';
      return 'Formato inválido.';
    }
    if (control.hasError('minlength')) {
      return 'Demasiado corto.';
    }
    if (control.hasError('maxlength')) {
      return 'Demasiado largo.';
    }
    return 'Valor inválido.';
  }

  protected onSubmit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const data: CustomerFormData = {
      personType: v.personType,
      firstName: v.firstName || undefined,
      lastName: v.lastName || undefined,
      razonSocial: v.personType === 'JURIDICA' ? v.razonSocial || undefined : undefined,
      dni: v.personType === 'FISICA' ? v.dni || undefined : undefined,
      cuitCuil: v.personType === 'JURIDICA' ? v.cuitCuil || undefined : undefined,
      taxCondition: v.taxCondition,
      phone: v.phone,
      email: v.email || undefined,
      dataConsent: v.dataConsent,
      minimumViable: this.minimumViable(),
    };
    this.submit.emit(data);
  }
}
