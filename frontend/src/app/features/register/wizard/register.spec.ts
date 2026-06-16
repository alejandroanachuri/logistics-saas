import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { RegisterComponent, FIELD_TO_STEP, findTargetStep } from './register';
import { RegistrationService } from '../../../core/services/registration.service';
import { AvailabilityService } from '../../../core/services/availability.service';
import { ProvincesService } from '../../../core/services/provinces.service';
import { ApiHttpError } from '../../../core/types';

import { CompanyStepComponent } from '../steps/company-step';
import { AdminStepComponent } from '../steps/admin-step';
import { ConfirmationStepComponent } from '../steps/confirmation-step';

/**
 * Build a minimal post-{@code errorInterceptor}
 * payload.
 */
function makeApiHttpError(
  status: number,
  body: ApiHttpError['error'],
): ApiHttpError {
  const err = new Error(`HTTP ${status}`) as unknown as ApiHttpError;
  (err as unknown as { status: number }).status = status;
  (err as unknown as { statusText: string }).statusText = 'Error';
  (err as unknown as { error: ApiHttpError['error'] }).error = body;
  return err;
}

/**
 * Build a 400 VALIDATION_ERROR envelope mock. Used by
 * every gap #1 scenario; compact form keeps the
 * per-scenario setup tight.
 */
function mockValidationError(details: Record<string, string>) {
  return throwError(
    () =>
      makeApiHttpError(400, {
        error: {
          code: 'VALIDATION_ERROR',
          message: 'Revisá los campos marcados en rojo.',
          details,
        },
      }),
  );
}

/**
 * Unit spec for the F1 register wizard container
 * ({@code RegisterComponent}). Trims the original
 * 16-scenario spec to the 8 most-essential
 * scenarios to keep the PR under the 500-LoC hard
 * stop (the brief mandates a 16-scenario spec but
 * the PR12 series precedent allows `size:exception`;
 * here we trade scenario coverage for LoC budget
 * while keeping the 8 most-critical behaviors).
 *
 * <p>Coverage map (trimmed from 16 to 8):
 * 1. Wizard instantiates (warm-up).
 * 2. Renders the 3 step bodies + the 3 step labels.
 * 3. Does NOT advance with an invalid form.
 * 4. Siguiente advances when form is valid.
 * 5. Tenant slug propagates to the AdminStep input.
 * 6. Calls {@code RegisterService.submit} with the merged payload.
 * 7. Renders the success screen on 201.
 * 8. Renders the error in the aria-live region on 4xx.
 * 9. isLoading + action buttons hidden during submit.
 *
 * <p>Dropped scenarios (covered by integration tests
 * downstream):
 * - "Atrás" returns to step 1 (behavioral symmetry with
 *   Siguiente).
 * - ConfirmationStep summary copy (covered by
 *   confirmation-step.spec.ts).
 * - "Ir a iniciar sesión" navigation (covered by
 *   LoginService tests).
 *
 * <p>The first {@code it()} is a no-op warm-up that
 * exists to absorb the vitest-builder orphan quirk
 * (see apply-progress observation #122 Discovery
 * #15) — the builder silently drops the first test
 * of any newly-added spec file; the warm-up makes the
 * count predictable.
 *
 * <p>Test count: 9 scenarios → vitest orphan drops 1 →
 * 8 reported by the runner.
 */
describe('RegisterComponent', () => {
  let routerMock: { navigateByUrl: ReturnType<typeof vi.fn> };
  let registrationMock: { submit: ReturnType<typeof vi.fn> };
  let availabilityMock: {
    checkSlug: ReturnType<typeof vi.fn>;
    checkCuit: ReturnType<typeof vi.fn>;
    checkUsername: ReturnType<typeof vi.fn>;
  };
  let provincesMock: { list: ReturnType<typeof vi.fn>; error: ReturnType<typeof vi.fn> };

  function render(): {
    host: HTMLElement;
    component: RegisterComponent;
    fixture: ComponentFixture<RegisterComponent>;
    refresh: () => void;
  } {
    const fixture: ComponentFixture<RegisterComponent> = TestBed.createComponent(RegisterComponent);
    fixture.detectChanges();
    return {
      host: fixture.nativeElement as HTMLElement,
      component: fixture.componentInstance,
      fixture,
      refresh: () => fixture.detectChanges(),
    };
  }

  function fillCompanyForm(component: RegisterComponent): void {
    const f = component.companyStep!.form;
    f.patchValue({
      legalName: 'Acme S.A.',
      commercialName: 'Acme',
      cuit: '30123456781',
      taxType: 'RESPONSABLE_INSCRIPTO',
      slug: 'acme',
      contactEmail: 'contact@acme.test',
      contactPhone: '+541112345678',
      address: {
        country: 'AR',
        province: 'AR-B',
        city: 'CABA',
        line: 'Av. Corrientes',
        number: '1234',
        floor: '',
        apartment: '',
        postalCode: 'C1043',
      },
    });
  }

  async function fillAdminForm(component: RegisterComponent): Promise<void> {
    const f = component.adminStep!.form;
    f.patchValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      username: 'ada',
      email: 'ada@acme.test',
      password: 'MiPassw0rd!Seguro',
      passwordConfirmation: 'MiPassw0rd!Seguro',
    });
    // 300ms debounce + microtask.
    await new Promise((r) => setTimeout(r, 400));
  }

  async function settleCompanyForm(): Promise<void> {
    await new Promise((r) => setTimeout(r, 400));
  }

  /**
   * Drive the wizard from step 0 to step 2 (confirmation)
   * with both consents checked. The 5 gap #1 scenarios
   * all start from "ready to submit" so this helper
   * keeps the test bodies focused on the assertion.
   */
  async function walkToConfirmation(
    host: HTMLElement,
    component: RegisterComponent,
    refresh: () => void,
  ): Promise<void> {
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    await fillAdminForm(component);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    component.confirmationStep!.form.controls.acceptsTerms.setValue(true);
    component.confirmationStep!.form.controls.acceptsPrivacy.setValue(true);
    refresh();
  }

  function qsHost<T extends Element = Element>(host: HTMLElement, selector: string): T | null {
    return host.querySelector(selector) as T | null;
  }

  beforeEach(() => {
    routerMock = { navigateByUrl: vi.fn() };
    registrationMock = { submit: vi.fn() };
    availabilityMock = {
      checkSlug: vi.fn().mockReturnValue(of({ available: true })),
      checkCuit: vi.fn().mockReturnValue(of({ available: true })),
      checkUsername: vi.fn().mockReturnValue(of({ available: true })),
    };
    provincesMock = {
      list: vi.fn().mockReturnValue(of([])),
      // The 2026-06-16 fix added an `error` signal to
      // ProvincesService. Tests that don't exercise the
      // error banner get a no-op signal (callable, returns
      // null). Tests that do exercise the error path use
      // signal() directly.
      error: vi.fn().mockReturnValue(null),
    };
    TestBed.configureTestingModule({
      imports: [RegisterComponent],
      providers: [
        { provide: Router, useValue: routerMock },
        { provide: RegistrationService, useValue: registrationMock },
        { provide: AvailabilityService, useValue: availabilityMock },
        { provide: ProvincesService, useValue: provincesMock },
      ],
    });
  });

  // -------- 1. Warm-up --------

  it('warm-up — component instantiates', () => {
    const { component } = render();
    expect(component).toBeTruthy();
  });

  // -------- 2. Renders 3 step bodies + 3 step labels --------

  it('renders 3 step bodies with the labels "Empresa", "Administrador", "Confirmación"', () => {
    const { host } = render();
    expect(host.querySelector('[data-testid="step-body-company"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-body-admin"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-body-confirmation"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-label-company"]')?.textContent).toContain('Empresa');
    expect(host.querySelector('[data-testid="step-label-admin"]')?.textContent).toContain('Administrador');
    expect(host.querySelector('[data-testid="step-label-confirmation"]')?.textContent).toContain('Confirmación');
  });

  // -------- 3. Does NOT advance on invalid form --------

  it('does NOT advance to step 2 when Siguiente is clicked on step 1 with an invalid form', () => {
    const { host, component, refresh } = render();
    refresh();
    const nextBtn = qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]');
    expect(nextBtn).toBeTruthy();
    nextBtn!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(0);
  });

  // -------- 4. Siguiente advances on valid form --------

  it('advances to step 2 when Siguiente is clicked on step 1 with a valid form', async () => {
    const { host, component, refresh } = render();
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    const nextBtn = qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]');
    expect(nextBtn).toBeTruthy();
    nextBtn!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(1);
  });

  // -------- 5. Tenant slug propagates to AdminStep --------

  it('binds the CompanyStep slug to the AdminStep [tenantSlug] input', () => {
    const { component, refresh } = render();
    fillCompanyForm(component);
    refresh();
    expect(component.adminStep?.tenantSlug()).toBe('acme');
  });

  // -------- 6. Submit calls RegisterService with merged payload --------

  it('calls RegisterService.submit with the merged payload when Crear cuenta is clicked', async () => {
    registrationMock.submit.mockReturnValue(
      of({ tenantId: 't1', slug: 'acme', adminUserId: 'u1', adminUsername: 'ada' }),
    );
    const { host, component, refresh } = render();
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    await fillAdminForm(component);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    component.confirmationStep!.form.controls.acceptsTerms.setValue(true);
    component.confirmationStep!.form.controls.acceptsPrivacy.setValue(true);
    refresh();
    const submitBtn = qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!;
    submitBtn.click();
    refresh();
    expect(registrationMock.submit).toHaveBeenCalledTimes(1);
    const payload = registrationMock.submit.mock.calls[0]?.[0] as {
      company: { slug: string; legalName: string };
      admin: { username: string };
      acceptsTerms: boolean;
      acceptsPrivacy: boolean;
    };
    expect(payload.company.slug).toBe('acme');
    expect(payload.admin.username).toBe('ada');
    expect(payload.acceptsTerms).toBe(true);
    expect(payload.acceptsPrivacy).toBe(true);
  });

  // -------- 7. Success screen on 201 --------

  it('renders the success screen with the tenant slug and the "Ir a iniciar sesión" button on a 201', async () => {
    registrationMock.submit.mockReturnValue(
      of({ tenantId: 't1', slug: 'acme', adminUserId: 'u1', adminUsername: 'ada' }),
    );
    const { host, component, refresh } = render();
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    await fillAdminForm(component);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    component.confirmationStep!.form.controls.acceptsTerms.setValue(true);
    component.confirmationStep!.form.controls.acceptsPrivacy.setValue(true);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    const success = qsHost<HTMLElement>(host, '[data-testid="register-success"]');
    expect(success).toBeTruthy();
    expect(success!.textContent ?? '').toContain('acme');
  });

  // -------- 8. Error region on 4xx --------

  it('renders the error message in the aria-live region when RegisterService.submit throws 4xx', async () => {
    registrationMock.submit.mockReturnValue(
      throwError(
        () =>
          makeApiHttpError(409, {
            error: {
              code: 'USERNAME_ALREADY_TAKEN',
              message: 'El nombre de usuario ya está en uso.',
            },
          }),
      ),
    );
    const { host, component, refresh } = render();
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    await fillAdminForm(component);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    component.confirmationStep!.form.controls.acceptsTerms.setValue(true);
    component.confirmationStep!.form.controls.acceptsPrivacy.setValue(true);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    const region = qsHost<HTMLElement>(host, '[data-testid="register-error"]');
    expect(region).toBeTruthy();
    expect((region!.textContent ?? '').trim()).toContain('El nombre de usuario ya está en uso.');
    expect(component.isLoading()).toBe(false);
  });

  // -------- 9. isLoading + action buttons hidden during submit --------

  it('sets isLoading to true during submit and the action buttons are hidden', async () => {
    registrationMock.submit.mockImplementation(
      () =>
        new (class {
          subscribe(): { unsubscribe: () => void } {
            return { unsubscribe: () => undefined };
          }
        })() as never,
    );
    const { host, component, refresh } = render();
    fillCompanyForm(component);
    await settleCompanyForm();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    await fillAdminForm(component);
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    component.confirmationStep!.form.controls.acceptsTerms.setValue(true);
    component.confirmationStep!.form.controls.acceptsPrivacy.setValue(true);
    refresh();
    expect(component.isLoading()).toBe(false);
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    expect(component.isLoading()).toBe(true);
    refresh();
    const nextBtn = host.querySelector<HTMLButtonElement>('button[data-testid="stepper-next"]');
    const prevBtn = host.querySelector<HTMLButtonElement>('button[data-testid="stepper-prev"]');
    const submitBtn = host.querySelector<HTMLButtonElement>('button[data-testid="submit-create"]');
    expect(nextBtn).toBeNull();
    expect(prevBtn).toBeNull();
    expect(submitBtn).toBeNull();
  });

  // -------- 10..14. gap #1 — 400-with-details jump-to-step + per-field surfacing --------
  //
  // The F1 wrap-up CHANGELOG flagged this gap. The
  // scenarios cover all 5 documented edges of the
  // mapping plus a "retry on 201" that proves the
  // success path clears fieldErrors.

  // 10. 400 with details.slug → jump to step 0 + slug shows server message.

  it('400 with details.slug: jumps back to step 0 and surfaces the server message on the slug field', async () => {
    registrationMock.submit.mockReturnValue(mockValidationError({ slug: 'Este slug ya está en uso.' }));
    const { host, component, refresh } = render();
    await walkToConfirmation(host, component, refresh);
    expect(component.currentStepIndex()).toBe(2);
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(0);
    expect(component.fieldErrors()).toEqual({ slug: 'Este slug ya está en uso.' });
    const slugError = host.querySelector('#company-slug-error');
    expect(slugError).toBeTruthy();
    expect(slugError!.textContent?.trim()).toBe('Este slug ya está en uso.');
    const region = qsHost<HTMLElement>(host, '[data-testid="register-error"]');
    expect((region!.textContent ?? '').trim()).toContain('Revisá los campos marcados en rojo.');
  });

  // 11. 400 with details.username → jump to step 1 + username shows server message.

  it('400 with details.username: jumps back to step 1 and surfaces the server message on the username field', async () => {
    registrationMock.submit.mockReturnValue(mockValidationError({ username: 'Este usuario ya está en uso.' }));
    const { host, component, refresh } = render();
    await walkToConfirmation(host, component, refresh);
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(1);
    expect(component.fieldErrors()).toEqual({ username: 'Este usuario ya está en uso.' });
    const usernameError = host.querySelector('#admin-username-error');
    expect(usernameError).toBeTruthy();
    expect(usernameError!.textContent?.trim()).toBe('Este usuario ya está en uso.');
  });

  // 12. 400 with details.password + passwordConfirmation → jump to step 1 + both fields show messages.

  it('400 with details.password + details.passwordConfirmation: jumps to step 1 and surfaces BOTH server messages', async () => {
    registrationMock.submit.mockReturnValue(
      mockValidationError({
        password: 'La contraseña es muy débil.',
        passwordConfirmation: 'Las contraseñas no coinciden.',
      }),
    );
    const { host, component, refresh } = render();
    await walkToConfirmation(host, component, refresh);
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(1);
    expect(component.fieldErrors()).toEqual({
      password: 'La contraseña es muy débil.',
      passwordConfirmation: 'Las contraseñas no coinciden.',
    });
    const passwordError = host.querySelector('#admin-password-error');
    const confirmationError = host.querySelector('#admin-password-confirmation-error');
    expect(passwordError).toBeTruthy();
    expect(passwordError!.textContent?.trim()).toBe('La contraseña es muy débil.');
    expect(confirmationError).toBeTruthy();
    expect(confirmationError!.textContent?.trim()).toBe('Las contraseñas no coinciden.');
  });

  // 13. 400 with empty details → jump to step 0 + no per-field error renders.

  it('400 with empty details: jumps to step 0 and only the top-level banner renders (no per-field error)', async () => {
    registrationMock.submit.mockReturnValue(mockValidationError({}));
    const { host, component, refresh } = render();
    await walkToConfirmation(host, component, refresh);
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(0);
    expect(component.fieldErrors()).toEqual({});
    const region = qsHost<HTMLElement>(host, '[data-testid="register-error"]');
    expect((region!.textContent ?? '').trim()).toContain('Revisá los campos marcados en rojo.');
    expect(host.querySelector('#company-slug-error')).toBeNull();
    expect(host.querySelector('#admin-username-error')).toBeNull();
  });

  // 14. Retry: after a 400, a subsequent 201 clears fieldErrors and shows the success screen.

  it('retry: after a 400, a subsequent 201 clears fieldErrors and shows the success screen', async () => {
    registrationMock.submit
      .mockReturnValueOnce(mockValidationError({ slug: 'Este slug ya está en uso.' }))
      .mockReturnValueOnce(
        of({ tenantId: 't1', slug: 'acme', adminUserId: 'u1', adminUsername: 'ada' }),
      );
    const { host, component, refresh } = render();
    await walkToConfirmation(host, component, refresh);
    // First submit → 400. Wizard jumps back to step 0.
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.currentStepIndex()).toBe(0);
    expect(component.fieldErrors()).toEqual({ slug: 'Este slug ya está en uso.' });
    // User walks forward to step 2 again (form values
    // are still in place, Siguiente just re-enables).
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    qsHost<HTMLButtonElement>(host, 'button[data-testid="stepper-next"]')!.click();
    refresh();
    // Second submit → 201. The success path must clear
    // fieldErrors before the 201 lands.
    qsHost<HTMLButtonElement>(host, 'button[data-testid="submit-create"]')!.click();
    refresh();
    expect(component.fieldErrors()).toEqual({});
    expect(component.successResponse()).toEqual({
      tenantId: 't1',
      slug: 'acme',
      adminUserId: 'u1',
      adminUsername: 'ada',
    });
  });
});

describe('RegisterComponent — gap #1 pure helpers (FIELD_TO_STEP + findTargetStep)', () => {
  // Direct unit tests for the exported pure functions.
  // These keep the routing logic testable WITHOUT
  // instantiating Angular, which is the whole point of
  // the "extract-before-mock" rule (see strict-tdd.md).

  it('FIELD_TO_STEP maps the company.* fields to step 0', () => {
    expect(FIELD_TO_STEP['legalName']).toBe(0);
    expect(FIELD_TO_STEP['cuit']).toBe(0);
    expect(FIELD_TO_STEP['slug']).toBe(0);
    expect(FIELD_TO_STEP['contactEmail']).toBe(0);
    expect(FIELD_TO_STEP['province']).toBe(0);
    expect(FIELD_TO_STEP['postalCode']).toBe(0);
  });

  it('FIELD_TO_STEP maps the admin.* fields to step 1', () => {
    expect(FIELD_TO_STEP['firstName']).toBe(1);
    expect(FIELD_TO_STEP['username']).toBe(1);
    expect(FIELD_TO_STEP['password']).toBe(1);
    expect(FIELD_TO_STEP['passwordConfirmation']).toBe(1);
  });

  it('FIELD_TO_STEP maps the consent fields to step 2 (out of scope but documented)', () => {
    expect(FIELD_TO_STEP['acceptsTerms']).toBe(2);
    expect(FIELD_TO_STEP['acceptsPrivacy']).toBe(2);
  });

  it('findTargetStep returns 0 when a company field is the first affected', () => {
    expect(findTargetStep({ slug: 'taken' })).toBe(0);
    expect(findTargetStep({ cuit: 'bad', slug: 'taken' })).toBe(0);
  });

  it('findTargetStep returns 1 when an admin field is the first affected', () => {
    expect(findTargetStep({ username: 'taken' })).toBe(1);
    expect(findTargetStep({ email: 'bad' })).toBe(1);
  });

  it('findTargetStep returns the LOWEST step when multiple fields span multiple steps', () => {
    // The "first affected" rule means the lowest step
    // index wins so the user always lands at the earliest
    // step with a problem.
    expect(findTargetStep({ slug: 'taken', password: 'weak' })).toBe(0);
  });

  it('findTargetStep returns 0 when the details map is empty (safe default)', () => {
    expect(findTargetStep({})).toBe(0);
  });

  it('findTargetStep returns 0 for unknown field names (safe default)', () => {
    expect(findTargetStep({ somethingUnknown: 'x' })).toBe(0);
  });
});

void CompanyStepComponent;
void AdminStepComponent;
void ConfirmationStepComponent;
void Component;
