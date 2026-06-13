import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  AsyncValidatorFn,
  FormControl,
  FormGroup,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { Observable, of, timer } from 'rxjs';
import { catchError, map, switchMap } from 'rxjs/operators';

import { ProvincesService } from '../../../core/services/provinces.service';
import { AvailabilityService } from '../../../core/services/availability.service';

/**
 * Tax-type enum the backend's V5 migration accepts (see the
 * {@code tenant-registration} spec; mirrored in
 * {@code core/types/auth.ts:RegisterRequest.company.taxType}).
 */
export type TaxType = 'RESPONSABLE_INSCRIPTO' | 'MONOTRIBUTO' | 'EXENTO';

/**
 * Argentine CUIT check-digit algorithm — AFIP mod-11.
 * Mirrors the backend's
 * {@code ar.com.logistics.common.validation.CuitValidator}
 * (PR2b1, Java reference). The pure-TS port is exported so
 * the spec can call it without instantiating Angular
 * Validators. The 11-digit form is the canonical input
 * (the UI strips dashes client-side before calling).
 *
 * <p>Weight vector (left-to-right, 10 leading digits):
 * {@code 5,4,3,2,7,6,5,4,3,2}. Modulo 11 is taken; the
 * canonical check digit is {@code 11 - mod} (with the
 * substitutions {@code 11 -> 0} and {@code 10 -> 9} for the
 * CUITs starting with 23 or 33).
 */
export function cuitIsValid(digits: string): boolean {
  if (!/^\d{11}$/.test(digits)) {
    return false;
  }
  const mult = [5, 4, 3, 2, 7, 6, 5, 4, 3, 2];
  let sum = 0;
  for (let i = 0; i < 10; i++) {
    sum += Number(digits[i]) * mult[i];
  }
  const remainder = sum % 11;
  let check = 11 - remainder;
  if (check === 11) {
    check = 0;
  }
  if (check === 10) {
    // 23- and 33-prefix CUITs: mod-11 yields 10; AFIP
    // canonical substitution is 9.
    check = 9;
  }
  return check === Number(digits[10]);
}

/**
 * Angular {@code ValidatorFn} adapter for {@code cuitIsValid}.
 * Returns {@code { cuit: { valid: false } }} on a bad check
 * digit; {@code null} on a valid CUIT. Companion
 * {@code Validators.pattern(/^\d{11}$/)} runs first; this
 * validator only runs when the pattern matches, so it never
 * re-checks length.
 */
export function cuitMod11Validator(): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = control.value;
    if (typeof value !== 'string' || value === '') {
      return null;
    }
    if (!/^\d{11}$/.test(value)) {
      return null;
    }
    return cuitIsValid(value) ? null : { cuit: { valid: false } };
  };
}

/**
 * 300ms-debounced async validator for slug availability.
 * Returns {@code { slugTaken: true }} when the backend
 * reports the slug is taken; {@code null} otherwise. Network
 * errors are swallowed (they don't block the form).
 *
 * <p>The {@code timer(300) + switchMap(...)} is the debounce
 * pattern. Every keystroke restarts the 300ms clock; only
 * the last value reaches the API.
 */
export function slugAvailableAsync(
  availability: AvailabilityService,
): AsyncValidatorFn {
  return (control: AbstractControl): Observable<ValidationErrors | null> => {
    const value = control.value;
    if (typeof value !== 'string' || value === '') {
      return of(null);
    }
    return timer(300).pipe(
      switchMap(() => availability.checkSlug(value)),
      map((resp) => (resp.available ? null : { slugTaken: true })),
      catchError(() => of(null)),
    );
  };
}

/**
 * 300ms-debounced async validator for CUIT availability.
 * Returns {@code { cuitTaken: true }} when the backend
 * reports the CUIT is already registered; {@code null}
 * otherwise. Same debounce plumbing as
 * {@code slugAvailableAsync}.
 */
export function cuitAvailableAsync(
  availability: AvailabilityService,
): AsyncValidatorFn {
  return (control: AbstractControl): Observable<ValidationErrors | null> => {
    const value = control.value;
    if (typeof value !== 'string' || value === '') {
      return of(null);
    }
    return timer(300).pipe(
      switchMap(() => availability.checkCuit(value)),
      map((resp) => (resp.available ? null : { cuitTaken: true })),
      catchError(() => of(null)),
    );
  };
}

/** Typed shape of the step 1 company-data form. */
export type CompanyFormGroup = FormGroup<{
  legalName: FormControl<string>;
  commercialName: FormControl<string>;
  cuit: FormControl<string>;
  taxType: FormControl<TaxType>;
  slug: FormControl<string>;
  contactEmail: FormControl<string>;
  contactPhone: FormControl<string>;
  address: FormGroup<{
    country: FormControl<string>;
    province: FormControl<string>;
    city: FormControl<string>;
    line: FormControl<string>;
    number: FormControl<string>;
    floor: FormControl<string>;
    apartment: FormControl<string>;
    postalCode: FormControl<string>;
  }>;
}>;

/**
 * Step 1 of the F1 register wizard. Renders a typed
 * reactive {@code FormGroup} with 15 controls (7 root
 * controls + 8 nested address controls) and the supporting
 * scaffolding (province select populated from
 * {@code ProvincesService.list()}, tax type select, Siguiente
 * button, aria-live error region).
 *
 * <p>Standalone Angular 21 component — no {@code NgModule}.
 * Lives in the future stepper as
 * {@code <app-company-step></app-company-step>}. The stepper
 * is owned by PR12c; this PR12b1 component does NOT expose
 * its form via {@code @Input()} / {@code @Output()} — the
 * parent stepper reaches into {@code this.form} via
 * {@code @ViewChild} (PR12c scope).
 *
 * <p>Sync validation matches the backend validators (PR2b1).
 * Async validation (slug + cuit availability) is wired with
 * a 300ms debounce — the form is {@code form.pending}
 * while the async validators run, which is what the
 * Siguiente button's {@code canAdvance} gate reads.
 */
@Component({
  selector: 'app-company-step',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  templateUrl: './company-step.html',
})
export class CompanyStepComponent {
  readonly form: CompanyFormGroup;

  private readonly provincesService = inject(ProvincesService);
  private readonly availability = inject(AvailabilityService);

  /**
   * Province list surfaced as a signal for the template
   * (uses {@code toSignal(list(), { initialValue: [] })} per
   * PR12a Discovery #27).
   */
  readonly provinces = toSignal(this.provincesService.list(), { initialValue: [] });

  constructor(private readonly fb: NonNullableFormBuilder) {
    // Address defaults (country = 'AR', province = 'AR-B') are
    // seeded in the initial value so the province <select>
    // shows Buenos Aires by default.
    this.form = this.fb.group({
      legalName: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(2), Validators.maxLength(80)],
      }),
      commercialName: this.fb.control('', { validators: [Validators.maxLength(80)] }),
      cuit: this.fb.control('', {
        validators: [Validators.required, Validators.pattern(/^\d{11}$/), cuitMod11Validator()],
        asyncValidators: [cuitAvailableAsync(this.availability)],
        updateOn: 'change',
      }),
      taxType: this.fb.control<TaxType>('RESPONSABLE_INSCRIPTO' as TaxType, {
        validators: [Validators.required],
      }),
      slug: this.fb.control('', {
        validators: [Validators.required, Validators.pattern(/^[a-z][a-z0-9]{1,11}$/)],
        asyncValidators: [slugAvailableAsync(this.availability)],
        updateOn: 'change',
      }),
      contactEmail: this.fb.control('', { validators: [Validators.required, Validators.email] }),
      contactPhone: this.fb.control('', {
        validators: [Validators.required, Validators.pattern(/^[+]?[\d\s()-]{6,20}$/)],
      }),
      address: this.fb.group({
        country: this.fb.control('AR', { validators: [Validators.required] }),
        province: this.fb.control('AR-B', { validators: [Validators.required] }),
        city: this.fb.control('', {
          validators: [Validators.required, Validators.minLength(2), Validators.maxLength(60)],
        }),
        line: this.fb.control('', {
          validators: [Validators.required, Validators.minLength(3), Validators.maxLength(120)],
        }),
        number: this.fb.control('', { validators: [Validators.required, Validators.maxLength(10)] }),
        floor: this.fb.control('', { validators: [Validators.maxLength(10)] }),
        apartment: this.fb.control('', { validators: [Validators.maxLength(10)] }),
        postalCode: this.fb.control('', {
          validators: [Validators.required, Validators.pattern(/^[A-Z\d]{4,8}$/i)],
        }),
      }),
    }) as CompanyFormGroup;
  }

  /** Siguiente button gate: form must be valid AND not pending. */
  get canAdvance(): boolean {
    return this.form.valid && !this.form.pending;
  }

  /** Top-level error copy for the aria-live region. */
  get topLevelError(): string {
    if (this.form.pending || this.form.valid) {
      return '';
    }
    return 'Revisá los campos marcados en rojo.';
  }

  /**
   * Per-field Spanish error copy. Returns {@code null}
   * when the control has no errors or has not been
   * touched / dirtied — the template binds the result to
   * an {@code @if} block so the DOM stays empty on first
   * render (avoids layout jump + premature "required"
   * shouting at the user).
   *
   * <p>The order of the {@code if} chain matches the
   * order of the validators on each control; the first
   * match wins. The mapping covers all sync + async error
   * keys that any company-step control can produce.
   */
  errorMessageFor(control: AbstractControl | null): string | null {
    if (!control || !control.errors) return null;
    if (!this.shouldShowError(control)) return null;
    const errs = control.errors;
    if (errs['required']) return 'Este campo es obligatorio.';
    if (errs['email']) return 'Ingresá un email válido.';
    if (errs['minlength'])
      return `Mínimo ${(errs['minlength'] as { requiredLength: number }).requiredLength} caracteres.`;
    if (errs['maxlength'])
      return `Máximo ${(errs['maxlength'] as { requiredLength: number }).requiredLength} caracteres.`;
    if (errs['pattern']) return 'Formato inválido.';
    if (errs['cuit']) return 'CUIT inválido. Verificá el dígito verificador.';
    if (errs['slugTaken']) return 'Este slug ya está en uso.';
    if (errs['cuitTaken']) return 'Este CUIT ya está registrado.';
    return 'Revisá este campo.';
  }

  /**
   * True when the control is invalid AND has been
   * touched or dirtied. Shared by the template's
   * {@code aria-invalid}, {@code aria-describedby}, and
   * {@code border-red-500} bindings so the three stay in
   * lock-step. Extracted from the helper so the template
   * can call the cheap boolean without rebuilding the
   * error string.
   */
  shouldShowError(control: AbstractControl | null): boolean {
    if (!control) return false;
    return control.invalid && (control.touched || control.dirty);
  }
}
