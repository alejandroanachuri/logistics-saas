import { ChangeDetectionStrategy, Component, Signal, inject, input, signal } from '@angular/core';
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

import { AvailabilityService } from '../../../core/services/availability.service';
import { PasswordStrengthIndicator } from '../../../shared/forms/password-strength-indicator';

/**
 * Cross-field validator factory. Returns a
 * {@code ValidatorFn} that compares the bound control's
 * value to a sibling control's value (looked up by name
 * from {@code control.parent.get(targetName)}). Used by
 * {@code passwordConfirmation} to enforce that
 * {@code passwordConfirmation === password}.
 *
 * <p>If the control is not yet attached to a form
 * ({@code control.parent} is {@code null}) or the sibling
 * control is not present, the validator is a no-op and
 * returns {@code null} — Angular wires the validator
 * before the form group is fully assembled in some
 * lifecycle moments, so the missing-parent case is real.
 */
export function matchValidator(targetControlName: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    if (!control.parent) {
      return null;
    }
    const target = control.parent.get(targetControlName);
    if (!target) {
      return null;
    }
    return control.value === target.value ? null : { mismatch: true };
  };
}

/**
 * 300ms-debounced async validator for per-tenant username
 * availability. The username is unique per
 * {@code (tenantSlug, username)}, so the slug is required
 * alongside the username at the validator's call site.
 *
 * <p>Reads the slug from the {@code tenantSlug} signal on
 * every call so the validator picks up stepper-side changes
 * to the slug without a re-instantiation of the validator
 * closure. If {@code tenantSlug()} is empty (e.g. the
 * stepper has not yet bound the input), the validator is a
 * no-op and returns {@code of(null)} so the form is not
 * blocked by a missing cross-step input.
 *
 * <p>Returns {@code { usernameTaken: true }} when the
 * backend reports the username is taken; {@code null}
 * otherwise. Network errors are swallowed — they don't
 * block the form, mirroring the
 * {@code slugAvailableAsync}/{@code cuitAvailableAsync}
 * pattern from the company step (PR12b1).
 */
export function usernameAvailableAsync(
  availability: AvailabilityService,
  tenantSlug: Signal<string>,
): AsyncValidatorFn {
  return (control: AbstractControl): Observable<ValidationErrors | null> => {
    const value = control.value;
    const slug = tenantSlug();
    if (typeof value !== 'string' || value === '' || !slug) {
      return of(null);
    }
    return timer(300).pipe(
      switchMap(() => availability.checkUsername(slug, value)),
      map((resp) => (resp.available ? null : { usernameTaken: true })),
      catchError(() => of(null)),
    );
  };
}

/** Typed shape of the step 2 admin-user form. */
export type AdminFormGroup = FormGroup<{
  firstName: FormControl<string>;
  lastName: FormControl<string>;
  username: FormControl<string>;
  email: FormControl<string>;
  password: FormControl<string>;
  passwordConfirmation: FormControl<string>;
}>;

/**
 * Pure helper: extracts the form control's name from
 * its parent's {@code controls} Record. Returns
 * {@code null} for a control not attached to a form.
 *
 * <p>Used by the gap #1 server-error wiring: the step
 * component's {@code errorMessageFor} /
 * {@code shouldShowError} helpers look up the
 * {@code fieldErrors} record by the control's name so
 * the template stays unchanged (no second arg on every
 * call site). Same shape as the company-step helper
 * (kept as a private copy here to avoid a shared
 * {@code shared/forms/} import for two consumers — F2
 * can extract when a third consumer appears).
 */
function controlName(control: AbstractControl | null): string | null {
  if (!control || !control.parent) {
    return null;
  }
  const parent = control.parent as unknown as {
    controls?: Record<string, AbstractControl>;
  };
  if (!parent.controls) {
    return null;
  }
  for (const [name, sibling] of Object.entries(parent.controls)) {
    if (sibling === control) {
      return name;
    }
  }
  return null;
}

/**
 * Step 2 of the F1 register wizard. Renders a typed
 * reactive {@code FormGroup} with 6 controls and the
 * supporting scaffolding (password show/hide toggle,
 * password strength indicator, Siguiente button,
 * aria-live error region).
 *
 * <p>Standalone Angular 21 component — no
 * {@code NgModule}. Lives in the future stepper as
 * {@code <app-admin-step [tenantSlug]="companySlug">
 * </app-admin-step>}. The {@code tenantSlug} input is
 * bound by the parent stepper (PR12c scope) from the
 * CompanyStep's form value.
 *
 * <p>Sync validation mirrors the backend
 * {@code UsernameValidator} / {@code PasswordValidator}
 * (PR2b1) so the form is blocked client-side with the
 * same constraints the server enforces. The
 * {@code matchValidator} factory enforces
 * {@code passwordConfirmation === password} cross-field.
 * The async {@code usernameAvailableAsync} is per-tenant
 * (the username is unique per {@code (slug, username)})
 * and runs on a 300ms debounce.
 *
 * <p>The component does NOT expose its {@code form} as
 * {@code @Input()} / {@code @Output()} — the parent
 * stepper (PR12c) reaches into {@code this.form} via
 * {@code @ViewChild}, same pattern as
 * {@code CompanyStepComponent} (PR12b1).
 */
@Component({
  selector: 'app-admin-step',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, PasswordStrengthIndicator],
  templateUrl: './admin-step.html',
})
export class AdminStepComponent {
  readonly form: AdminFormGroup;

  /**
   * The company slug chosen in the CompanyStep (PR12b1).
   * Bound by the future stepper (PR12c) via
   * {@code [tenantSlug]="companySlug"}. The
   * {@code usernameAvailableAsync} validator reads this
   * signal on every call so a stepper-side change to the
   * slug is picked up without a re-instantiation.
   *
   * <p>Defaults to {@code ''} so the form is usable in
   * isolation (e.g. the AdminStep's own spec, or a future
   * standalone preview). When empty, the async username
   * validator returns {@code of(null)} (no API call).
   */
  readonly tenantSlug = input<string>('');

  /**
   * Per-field server-side validation errors, keyed by
   * the backend's flat {@code RegisterRequest} field
   * name (e.g. {@code "username"},
   * {@code "passwordConfirmation"}). Bound by the
   * wizard ({@code RegisterComponent}) from the 400
   * {@code VALIDATION_ERROR} envelope's
   * {@code details} map, projected to the keys owned by
   * this step via
   * {@code projectedFieldErrorsForStep(1)}.
   *
   * <p>Defaults to an empty record so the step renders
   * normally when the wizard has no server errors to
   * show. When non-empty, the matching input renders
   * the server's message inline via
   * {@code errorMessageFor} (the server message takes
   * precedence over the local sync / async validator
   * errors). Gap #1 of the F1 wrap-up CHANGELOG.
   */
  readonly fieldErrors = input<Record<string, string>>({});

  /**
   * Password visibility — read by the template to decide
   * whether the password input renders as
   * {@code type="password"} (masked) or {@code type="text"}
   * (visible). Mirrors the {@code LoginComponent} pattern
   * (PR11c).
   */
  readonly isPasswordVisible = signal(false);

  private readonly availability = inject(AvailabilityService);

  constructor(private readonly fb: NonNullableFormBuilder) {
    this.form = this.fb.group({
      firstName: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(2), Validators.maxLength(60)],
      }),
      lastName: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(2), Validators.maxLength(60)],
      }),
      username: this.fb.control('', {
        validators: [
          Validators.required,
          Validators.pattern(/^[a-z][a-z0-9._-]{2,29}$/),
        ],
        asyncValidators: [usernameAvailableAsync(this.availability, this.tenantSlug)],
        updateOn: 'change',
      }),
      email: this.fb.control('', {
        validators: [Validators.required, Validators.email, Validators.maxLength(120)],
      }),
      password: this.fb.control('', {
        validators: [Validators.required, Validators.minLength(8), Validators.maxLength(128)],
      }),
      passwordConfirmation: this.fb.control('', {
        validators: [Validators.required, matchValidator('password')],
      }),
    }) as AdminFormGroup;
  }

  /**
   * Flip the password visibility. Called by the show/hide
   * toggle button in the template (a {@code type="button"}
   * so it does not submit the form).
   */
  togglePasswordVisibility(): void {
    this.isPasswordVisible.update((v) => !v);
  }

  /**
   * Siguiente button gate: form must be valid AND not
   * pending (pending while the async username validator
   * runs after the 300ms debounce).
   */
  get canAdvance(): boolean {
    return this.form.valid && !this.form.pending;
  }

  /**
   * Top-level error copy for the aria-live region.
   * Mirrors the {@code CompanyStepComponent.topLevelError}
   * pattern (PR12b1).
   */
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
   * <p>The mapping covers the error keys that the admin
   * step's 6 controls can produce: required, email, the
   * username pattern, password min/max length, the
   * username async validator ({@code usernameTaken}), and
   * the cross-field {@code mismatch} validator on
   * {@code passwordConfirmation}.
   *
   * <p>Server-side errors (gap #1, 400
   * {@code VALIDATION_ERROR} envelope's
   * {@code details} map) take precedence over the local
   * sync / async validator errors and are visible
   * regardless of {@code touched} / {@code dirty} state.
   */
  errorMessageFor(control: AbstractControl | null): string | null {
    // 1. Server-side error takes precedence.
    const name = controlName(control);
    if (name) {
      const serverMsg = this.fieldErrors()[name];
      if (serverMsg) {
        return serverMsg;
      }
    }
    // 2. Local validator error (gated on touched/dirty).
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
    if (errs['usernameTaken']) return 'Este usuario ya existe.';
    if (errs['mismatch']) return 'Las contraseñas no coinciden.';
    return 'Revisá este campo.';
  }

  /**
   * True when the control is invalid AND has been
   * touched or dirtied — OR when the server has a
   * validation error for the control's field name
   * (gap #1: the 400 {@code VALIDATION_ERROR} path).
   * Shared by the template's {@code aria-invalid},
   * {@code aria-describedby}, and
   * {@code border-red-500} bindings so the three stay
   * in lock-step. Extracted from the helper so the
   * template can call the cheap boolean without
   * rebuilding the error string.
   */
  shouldShowError(control: AbstractControl | null): boolean {
    // Server-side error: visible regardless of touched state.
    const name = controlName(control);
    if (name && this.fieldErrors()[name]) {
      return true;
    }
    if (!control) return false;
    return control.invalid && (control.touched || control.dirty);
  }
}
