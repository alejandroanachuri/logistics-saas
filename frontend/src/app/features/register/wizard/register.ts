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
import { RegistrationService } from '../../../core/services/registration.service';
import { RegisterRequest, RegisterResponse } from '../../../core/types';

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
 */
@Component({
  selector: 'app-register',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CompanyStepComponent, AdminStepComponent, ConfirmationStepComponent],
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
        this.submitError.set(this.formatSubmitError(err));
      },
    });
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
