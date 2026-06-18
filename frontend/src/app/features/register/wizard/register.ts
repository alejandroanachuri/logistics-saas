import {
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { Router } from '@angular/router';

import { CompanyStepComponent } from '../steps/company-step';
import { AdminStepComponent } from '../steps/admin-step';
import { ConfirmationStepComponent } from '../steps/confirmation-step';
import { CardComponent } from '../../../shared/ui/card';
import { RegistrationService } from '../../../core/services/registration.service';
import { ApiErrorEnvelope, RegisterRequest, RegisterResponse } from '../../../core/types';

/**
 * Maps a backend {@code RegisterRequest} field name
 * (the keys of the {@code details} map in a 400
 * {@code VALIDATION_ERROR} envelope) to the wizard
 * step index that owns the field. Exported so
 * {@code findTargetStep} and the spec can call it
 * directly. The backend's {@code details} keys match
 * the {@code formControlName} values in the step
 * templates (no {@code company.}/ {@code admin.}{
 * prefix).
 */
export const FIELD_TO_STEP: Record<string, number> = {
  // step 0 — company data
  legalName: 0,
  commercialName: 0,
  cuit: 0,
  taxType: 0,
  slug: 0,
  contactEmail: 0,
  contactPhone: 0,
  // step 0 — address (treated as company.* by the backend)
  country: 0,
  province: 0,
  city: 0,
  line: 0,
  number: 0,
  floor: 0,
  apartment: 0,
  postalCode: 0,
  // step 1 — admin user data
  firstName: 1,
  lastName: 1,
  username: 1,
  email: 1,
  password: 1,
  passwordConfirmation: 1,
  // step 2 — consents (out of scope; included for the
  // future follow-up that wires the ConfirmationStep)
  acceptsTerms: 2,
  acceptsPrivacy: 2,
};

/**
 * Pure decision function: given the {@code details}
 * map from a 400 {@code VALIDATION_ERROR} envelope,
 * return the step index the wizard should jump to.
 * Rule: the LOWEST step index among the affected
 * fields wins (so the user lands at the earliest
 * step with a problem). Empty / unknown-only maps
 * default to 0.
 */
export function findTargetStep(details: Record<string, string> | undefined): number {
  if (!details) {
    return 0;
  }
  let lowest: number | null = null;
  for (const field of Object.keys(details)) {
    const step = FIELD_TO_STEP[field];
    if (step === undefined) {
      continue;
    }
    if (lowest === null || step < lowest) {
      lowest = step;
    }
  }
  return lowest ?? 0;
}

/**
 * The {@code /register} F1 page. Standalone Angular 21
 * component — no {@code NgModule}, no
 * {@code @angular/cdk/stepper}.
 *
 * <p>The wizard is a 3-step linear flow. Uses a
 * {@code currentStepIndex} signal + the
 * {@code canAdvance} / {@code canSubmit} computeds
 * to gate the wizard's navigation. The 3 step
 * bodies are always in the DOM (just hidden via
 * `[class.hidden]`) so the {@code @ViewChild}
 * references are stable across step transitions.
 *
 * <p>Form-validity wiring: a {@code formTick} signal
 * is incremented every time one of the step forms'
 * {@code statusChanges} fires. The {@code canAdvance}
 * computed reads this tick (forcing re-evaluation on
 * form state changes) plus the step's form
 * {@code .valid} getter. We do NOT use signal inputs
 * for the company/admin/confirmation forms because
 * those are owned by the step components (per the
 * PR12b1 / PR12b2 convention) and the stepper
 * reaches into them via {@code @ViewChild}.
 *
 * <p>Gap #1 (F1 wrap-up CHANGELOG) — server-side
 * validation errors: when the backend returns 400
 * with {@code code = VALIDATION_ERROR} and a
 * {@code details} map, the wizard jumps back to the
 * step that owns the first affected field, stores
 * the per-field messages in {@code fieldErrors},
 * and keeps the envelope's {@code message} in the
 * top-level {@code submitError} aria-live region as a
 * summary. The per-field rendering is owned by the
 * step components; the wizard only projects the
 * relevant subset via
 * {@code projectedFieldErrorsForStep}.
 */
@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CardComponent, CompanyStepComponent, AdminStepComponent, ConfirmationStepComponent],
  templateUrl: './register.html',
})
export class RegisterComponent {
  @ViewChild(CompanyStepComponent) readonly companyStep?: CompanyStepComponent;
  @ViewChild(AdminStepComponent) readonly adminStep?: AdminStepComponent;
  @ViewChild(ConfirmationStepComponent) readonly confirmationStep?: ConfirmationStepComponent;

  readonly currentStepIndex = signal(0);
  readonly isLoading = signal(false);
  readonly submitError = signal<string | null>(null);
  readonly successResponse = signal<RegisterResponse | null>(null);

  /**
   * Per-field server-side validation errors, keyed by
   * the backend's flat {@code RegisterRequest} field
   * name. Populated by the {@code submit()} error
   * handler when the backend returns 400 with
   * {@code code = VALIDATION_ERROR}; cleared at the
   * start of every submit and on a 201 success. The
   * wizard's template projects a per-step subset via
   * {@code projectedFieldErrorsForStep}. Gap #1 of the
   * F1 wrap-up CHANGELOG.
   */
  readonly fieldErrors = signal<Record<string, string>>({});

  /**
   * Tick signal that increments on every form
   * status change. Read by {@code canAdvance} so the
   * computed re-runs on every form change. The actual
   * validity read is in the switch below.
   */
  private readonly formTick = signal(0);

  readonly canAdvance = computed(() => {
    // Read the tick so this computed re-runs on
    // every form change. The actual validity read
    // is in the switch below.
    this.formTick();
    switch (this.currentStepIndex()) {
      case 0:
        return this.companyStep?.form.valid === true;
      case 1:
        return this.adminStep?.form.valid === true;
      case 2:
        return this.confirmationStep?.form.valid === true;
      default:
        return false;
    }
  });

  readonly canSubmit = computed(() => {
    this.formTick();
    return (
      this.currentStepIndex() === 2 &&
      this.confirmationStep?.form.valid === true &&
      !this.isLoading()
    );
  });

  private readonly registrationService = inject(RegistrationService);
  private readonly router = inject(Router);

  /**
   * Wire {@code statusChanges} subscriptions to the
   * 3 step forms AFTER the first render cycle. Using
   * {@code afterNextRender} (Angular 21) instead of
   * {@code queueMicrotask} guarantees the
   * {@code @ViewChild} references are populated
   * (queueMicrotask fires before the second CD cycle
   * in some configurations, leaving the references
   * still undefined — see PR13 / fix stepper-next
   * discovery). The callback runs once after the
   * first successful render and never again.
   */
  private readonly statusWire = afterNextRender(() => {
    this.companyStep?.form.statusChanges.subscribe(() => {
      this.formTick.update((n) => n + 1);
    });
    this.adminStep?.form.statusChanges.subscribe(() => {
      this.formTick.update((n) => n + 1);
    });
    this.confirmationStep?.form.statusChanges.subscribe(() => {
      this.formTick.update((n) => n + 1);
    });
  });

  next(): void {
    // Mark the current step's form as touched so any
    // unfocused invalid fields show their per-field
    // error message (PR13 fix). This is what the user
    // expects when clicking Siguiente with an
    // incomplete form: see which fields are missing.
    this.markCurrentStepTouched();
    if (!this.canAdvance()) {
      return;
    }
    if (this.currentStepIndex() < 2) {
      this.currentStepIndex.set(this.currentStepIndex() + 1);
    }
  }

  previous(): void {
    if (this.currentStepIndex() > 0) {
      this.currentStepIndex.set(this.currentStepIndex() - 1);
    }
  }

  /**
   * Mark every control in the current step's form as
   * touched so the per-field error messages (PR13
   * pattern in company-step.html / admin-step.html)
   * render their messages. Without this, the user
   * clicking Siguiente on an invalid form would not
   * see WHICH fields are wrong — the top-level
   * aria-live fires but the per-field <p role="alert">
   * stays hidden because the controls are pristine.
   */
  private markCurrentStepTouched(): void {
    switch (this.currentStepIndex()) {
      case 0:
        this.companyStep?.form.markAllAsTouched();
        return;
      case 1:
        this.adminStep?.form.markAllAsTouched();
        return;
      case 2:
        this.confirmationStep?.form.markAllAsTouched();
        return;
    }
  }

  tenantSlug(): string {
    return this.companyStep?.form.get('slug')?.value ?? '';
  }

  submit(): void {
    if (this.isLoading()) {
      return;
    }
    if (!this.companyStep || !this.adminStep || !this.confirmationStep) {
      return;
    }

    this.isLoading.set(true);
    this.submitError.set(null);
    // Reset the per-field errors from any previous failed
    // submit so the success path starts clean. The error
    // handler re-populates this when the server returns
    // 400 with `details`; a 5xx or network error leaves
    // the record empty.
    this.fieldErrors.set({});

    const companyValue = this.companyStep.form.value;
    const adminValue = this.adminStep.form.value;
    const consentValue = this.confirmationStep.form.value;

    const payload: RegisterRequest = {
      company: {
        legalName: companyValue.legalName ?? '',
        commercialName: companyValue.commercialName ?? '',
        cuit: companyValue.cuit ?? '',
        taxType: companyValue.taxType ?? 'RESPONSABLE_INSCRIPTO',
        slug: companyValue.slug ?? '',
        contactEmail: companyValue.contactEmail ?? '',
        contactPhone: companyValue.contactPhone ?? '',
        address: {
          country: companyValue.address?.country ?? 'AR',
          province: companyValue.address?.province ?? 'BUENOS_AIRES',
          city: companyValue.address?.city ?? '',
          line: companyValue.address?.line ?? '',
          number: companyValue.address?.number ?? '',
          floor: companyValue.address?.floor ?? '',
          apartment: companyValue.address?.apartment ?? '',
          postalCode: companyValue.address?.postalCode ?? '',
        },
      },
      admin: {
        firstName: adminValue.firstName ?? '',
        lastName: adminValue.lastName ?? '',
        username: adminValue.username ?? '',
        email: adminValue.email ?? '',
        password: adminValue.password ?? '',
        passwordConfirmation: adminValue.passwordConfirmation ?? '',
      },
      acceptsTerms: consentValue.acceptsTerms === true,
      acceptsPrivacy: consentValue.acceptsPrivacy === true,
    };

    this.registrationService.submit(payload).subscribe({
      next: (resp) => {
        this.isLoading.set(false);
        this.successResponse.set(resp);
      },
      error: (err: unknown) => {
        this.isLoading.set(false);
        this.handleSubmitError(err);
      },
    });
  }

  /**
   * Projection helper. Returns the subset of
   * {@code fieldErrors()} whose keys belong to the
   * given step. The wizard's template calls this once
   * per render to project the right slice into each
   * step's {@code [fieldErrors]} input. Method (not
   * computed) because the template re-evaluates it
   * with the current step on every CD cycle.
   */
  projectedFieldErrorsForStep(stepIndex: number): Record<string, string> {
    const projected: Record<string, string> = {};
    for (const [field, message] of Object.entries(this.fieldErrors())) {
      if (FIELD_TO_STEP[field] === stepIndex) {
        projected[field] = message;
      }
    }
    return projected;
  }

  /**
   * Error-handling seam for {@code submit()}. Splits
   * the 400-with-details path (gap #1) from every other
   * error. For 400 with {@code code = VALIDATION_ERROR}:
   * populate {@code fieldErrors}, jump
   * {@code currentStepIndex} to the lowest step that
   * owns an affected field, and set {@code submitError}
   * to the envelope {@code message} (top-level
   * summary). The step components then render each
   * per-field message inline through their existing
   * {@code errorMessageFor} helper.
   */
  private handleSubmitError(err: unknown): void {
    const validationDetails = this.extractValidationDetails(err);
    if (validationDetails !== null) {
      this.fieldErrors.set(validationDetails);
      this.currentStepIndex.set(findTargetStep(validationDetails));
    } else {
      this.fieldErrors.set({});
    }
    this.submitError.set(this.formatSubmitError(err));
  }

  /**
   * Pulls the {@code details} map from a 400
   * {@code VALIDATION_ERROR} envelope. Returns
   * {@code null} if the error is not a 400 with the
   * expected shape (409, 5xx, network failure) so the
   * caller falls through to the legacy top-level-only
   * path.
   */
  private extractValidationDetails(err: unknown): Record<string, string> | null {
    if (!err || typeof err !== 'object' || !('status' in err)) {
      return null;
    }
    const status = (err as { status: number }).status;
    if (status !== 400) {
      return null;
    }
    const body = (err as unknown as { error: unknown }).error;
    if (!body || typeof body !== 'object' || !('error' in body)) {
      return null;
    }
    const envelope = (body as ApiErrorEnvelope).error;
    if (!envelope || envelope.code !== 'VALIDATION_ERROR') {
      return null;
    }
    const details = envelope.details;
    if (!details || typeof details !== 'object') {
      return null;
    }
    // Coerce to a string-keyed, string-valued record to
    // match the `fieldErrors` signal's contract; the
    // backend always returns string values but a future
    // change shouldn't crash the wizard.
    const out: Record<string, string> = {};
    for (const [k, v] of Object.entries(details)) {
      out[k] = typeof v === 'string' ? v : String(v);
    }
    return out;
  }

  goToLogin(): void {
    void this.router.navigateByUrl('/login');
  }

  private formatSubmitError(err: unknown): string {
    if (err && typeof err === 'object' && 'error' in err) {
      const e = (err as { error: unknown }).error;
      if (e && typeof e === 'object' && 'error' in e) {
        const inner = (e as { error: { message?: string } }).error;
        if (inner && typeof inner.message === 'string') {
          return inner.message;
        }
      }
    }
    return 'Error al crear la cuenta. Reintentá.';
  }
}
