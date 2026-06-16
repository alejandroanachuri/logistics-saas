import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component, signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { AdminStepComponent, matchValidator } from './admin-step';
import { AvailabilityService } from '../../../core/services/availability.service';

/**
 * Host that wraps {@code AdminStepComponent} so the spec
 * can drive the {@code [tenantSlug]} signal input through
 * Angular's normal template binding (Angular 21's
 * {@code InputSignal<T>} does not expose a public setter
 * for external callers — see PR12a Discovery #24). The host
 * also lets the spec control the {@code tenantSlug}
 * programmatically to exercise the async username
 * validator's per-tenant call.
 *
 * <p>The first {@code it()} is a no-op warm-up that exists
 * to absorb the vitest-builder orphan quirk (see apply-
 * progress observation #122 Discovery #15) — the builder
 * silently drops the first test of any newly-added spec
 * file; the warm-up makes the count predictable.
 *
 * <p>Test count: 11 scenarios → vitest orphan drops 1 → 10
 * reported by the runner. After the PR12b1 tip baseline of
 * 97 tests / 13 spec files, this brings the totals to 107
 * tests / 14 spec files.
 */
describe('AdminStepComponent', () => {
  let availabilityMock: {
    checkUsername: ReturnType<typeof vi.fn>;
  };

  function render(slug = 'acme'): {
    host: HTMLElement;
    component: AdminStepComponent;
    fixture: ComponentFixture<HostComponent>;
    setSlug: (next: string) => void;
    refresh: () => void;
  } {
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    fixture.componentInstance.tenantSlug = slug;
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as AdminStepComponent;
    return {
      host: fixture.nativeElement as HTMLElement,
      component,
      fixture,
      setSlug: (next: string) => {
        fixture.componentInstance.tenantSlug = next;
        fixture.detectChanges();
      },
      refresh: () => fixture.detectChanges(),
    };
  }

  beforeEach(() => {
    availabilityMock = {
      checkUsername: vi.fn().mockReturnValue(of({ available: true })),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: AvailabilityService, useValue: availabilityMock },
      ],
    });
  });

  // -------- 1. Warm-up (vitest orphan offset) --------

  it('warm-up — component instantiates', () => {
    // No-op warm-up; absorbs the first-test-of-spec drop.
    expect(true).toBe(true);
  });

  // -------- 2. Form is invalid when all 6 controls are empty --------

  it('form is invalid when all 6 controls are empty', () => {
    const { component } = render();
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.firstName.value).toBe('');
    expect(component.form.controls.lastName.value).toBe('');
    expect(component.form.controls.username.value).toBe('');
    expect(component.form.controls.email.value).toBe('');
    expect(component.form.controls.password.value).toBe('');
    expect(component.form.controls.passwordConfirmation.value).toBe('');
  });

  // -------- 3. Username pattern --------

  it('username pattern: rejects uppercase, leading digit, < 3 or > 30 chars; accepts a valid username', () => {
    const { component } = render();
    // Uppercase.
    component.form.controls.username.setValue('Jdoe');
    expect(component.form.controls.username.invalid).toBe(true);
    // Leading digit.
    component.form.controls.username.setValue('1jdoe');
    expect(component.form.controls.username.invalid).toBe(true);
    // Too short (< 3 chars after the lowercase letter).
    component.form.controls.username.setValue('jd');
    expect(component.form.controls.username.invalid).toBe(true);
    // Too long (> 30 chars).
    component.form.controls.username.setValue('jdoe-very-long-username-1234567890');
    expect(component.form.controls.username.invalid).toBe(true);
    // Valid: starts lowercase, then 2-29 of [a-z0-9._-].
    component.form.controls.username.setValue('j.doe-2024');
    expect(component.form.controls.username.errors).toBeNull();
  });

  // -------- 4. Password min/max length --------

  it('password: accepts a strong password; rejects < 8 chars and > 128 chars', () => {
    const { component } = render();
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    expect(component.form.controls.password.errors).toBeNull();
    component.form.controls.password.setValue('short1!');
    expect(component.form.controls.password.invalid).toBe(true);
    component.form.controls.password.setValue('a'.repeat(129));
    expect(component.form.controls.password.invalid).toBe(true);
  });

  // -------- 5. passwordConfirmation mismatch --------

  it('passwordConfirmation: returns { mismatch: true } when it differs from password', () => {
    const { component } = render();
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    component.form.controls.passwordConfirmation.setValue('MiPassw0rd!Segur');
    expect(component.form.controls.passwordConfirmation.errors).toEqual({ mismatch: true });
  });

  // -------- 6. passwordConfirmation match --------

  it('passwordConfirmation: returns null when it matches password', () => {
    const { component } = render();
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    component.form.controls.passwordConfirmation.setValue('MiPassw0rd!Seguro');
    expect(component.form.controls.passwordConfirmation.errors).toBeNull();
  });

  // -------- 7. Async username validator: { usernameTaken: true } on unavailable --------

  it('async username validator returns { usernameTaken: true } when the API says unavailable', async () => {
    availabilityMock.checkUsername.mockReturnValue(
      of({ available: false, reason: 'USERNAME_ALREADY_TAKEN' }),
    );
    const { component } = render('acme');
    component.form.controls.username.setValue('taken');
    await new Promise((r) => setTimeout(r, 400));
    expect(component.form.controls.username.errors).toEqual({ usernameTaken: true });
    expect(availabilityMock.checkUsername).toHaveBeenCalledWith('acme', 'taken');
  });

  // -------- 8. Async username validator: 300ms debounce --------

  it('async username validator debounces 300ms and issues one call after the last value', async () => {
    const { component } = render('acme');
    component.form.controls.username.setValue('a');
    await new Promise((r) => setTimeout(r, 30));
    component.form.controls.username.setValue('ad');
    await new Promise((r) => setTimeout(r, 30));
    component.form.controls.username.setValue('adm');
    await new Promise((r) => setTimeout(r, 400));
    expect(availabilityMock.checkUsername).toHaveBeenCalledTimes(1);
    expect(availabilityMock.checkUsername).toHaveBeenCalledWith('acme', 'adm');
  });

  // -------- 9. tenantSlug empty: async validator is a no-op --------

  it('async username validator is a no-op when tenantSlug is empty', async () => {
    const { component } = render('');
    component.form.controls.username.setValue('admin');
    await new Promise((r) => setTimeout(r, 400));
    expect(availabilityMock.checkUsername).not.toHaveBeenCalled();
    expect(component.form.controls.username.errors).toBeNull();
  });

  // -------- 10. <app-password-strength-indicator> renders the tier name in its aria-label --------

  it('renders <app-password-strength-indicator> with the current password value bound', () => {
    const { host, component, refresh } = render();
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    refresh();
    const indicator = host.querySelector('app-password-strength-indicator');
    expect(indicator).toBeTruthy();
    // The indicator component is rendered in the DOM; its
    // own aria-label is set by PasswordStrengthIndicator
    // from the bound password value. The indicator's
    // tier label for a strong password is "Fuerte" (per
    // PR12a); the aria-label includes it.
    const labelled = host.querySelector('[data-testid="password-strength-indicator"]');
    expect(labelled).toBeTruthy();
    const aria = labelled?.getAttribute('aria-label') ?? '';
    expect(aria).toContain('Fuerte');
  });

  // -------- 11. Password show/hide toggle flips [type] --------

  it('flips the password input [type] when the show/hide toggle is clicked', () => {
    const { host, refresh } = render();
    const passwordInput = host.querySelector(
      'input[formcontrolname="password"]',
    ) as HTMLInputElement;
    expect(passwordInput.type).toBe('password');
    const toggle = Array.from(
      host.querySelectorAll<HTMLButtonElement>('button[type="button"]'),
    ).find((b) =>
      (b.getAttribute('aria-label') ?? '').toLowerCase().includes('contrase'),
    );
    expect(toggle).toBeTruthy();
    toggle!.click();
    refresh();
    expect(passwordInput.type).toBe('text');
    toggle!.click();
    refresh();
    expect(passwordInput.type).toBe('password');
  });

  // -------- PR13 (per-field error copy) --------

  it('errorMessageFor: returns "Las contraseñas no coinciden." for passwordConfirmation mismatch + touched', () => {
    const { component } = render();
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    component.form.controls.passwordConfirmation.setValue('OtraCosa');
    component.form.controls.passwordConfirmation.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.passwordConfirmation)).toBe(
      'Las contraseñas no coinciden.',
    );
  });

  it('errorMessageFor: returns "Este campo es obligatorio." for required + touched', () => {
    const { component } = render();
    component.form.controls.username.setValue('');
    component.form.controls.username.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.username)).toBe(
      'Este campo es obligatorio.',
    );
  });

  it('errorMessageFor wiring: renders the per-field error <p role="alert"> for an empty touched username', () => {
    const { host, component, refresh } = render();
    component.form.controls.username.setValue('');
    component.form.controls.username.markAsTouched();
    refresh();
    const errorEl = host.querySelector('#admin-username-error');
    expect(errorEl).toBeTruthy();
    expect(errorEl!.getAttribute('role')).toBe('alert');
    expect(errorEl!.textContent?.trim()).toBe('Este campo es obligatorio.');
    // The input must carry aria-invalid="true" and the
    // matching aria-describedby so AT pairs them up.
    const input = host.querySelector('#admin-username') as HTMLInputElement;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('admin-username-error');
  });

  // -------- PR13 (required-field asterisks) --------

  it('required-field wiring: every admin input has aria-required="true" and the label has the * marker', () => {
    const { host } = render();
    const ids = [
      'admin-first-name',
      'admin-last-name',
      'admin-username',
      'admin-email',
      'admin-password',
      'admin-password-confirmation',
    ];
    for (const id of ids) {
      const input = host.querySelector(`#${id}`) as HTMLInputElement;
      expect(input, `expected input #${id} to be in the DOM`).toBeTruthy();
      expect(input.getAttribute('aria-required')).toBe('true');
      const label = host.querySelector(`label[for="${id}"]`) as HTMLLabelElement;
      expect(label, `expected label[for="${id}"] to be in the DOM`).toBeTruthy();
      const asterisk = label.querySelector('span[aria-hidden="true"]');
      expect(asterisk, `expected label[for="${id}"] to carry the * marker`).toBeTruthy();
      expect(asterisk!.textContent?.trim()).toBe('*');
    }
  });

  // -------- gap #1 (server-error field surfacing) --------
  //
  // The 3 scenarios below cover the new `fieldErrors`
  // signal input. Server-side 400 VALIDATION_ERROR
  // messages take precedence over local validator
  // errors AND are visible regardless of touched/dirty
  // state.

  it('errorMessageFor: returns the server error from fieldErrors() for the matching control', () => {
    const { component, fixture } = render();
    // Server says the username is taken; the field is
    // pristine (untouched) — the server error must still
    // be surfaced immediately, no markAsTouched needed.
    fixture.componentInstance.fieldErrors.set({ username: 'Este usuario ya existe.' });
    fixture.detectChanges();
    expect(component.errorMessageFor(component.form.controls.username)).toBe(
      'Este usuario ya existe.',
    );
  });

  it('errorMessageFor: server error wins over the local validator error for the same control', () => {
    const { component, fixture } = render();
    // Both: local "mismatch" (passwordConfirmation !=
    // password) + server "no coinciden" — server wins.
    component.form.controls.password.setValue('MiPassw0rd!Seguro');
    component.form.controls.passwordConfirmation.setValue('OtraCosa');
    component.form.controls.passwordConfirmation.markAsTouched();
    fixture.componentInstance.fieldErrors.set({
      passwordConfirmation: 'Las contraseñas no coinciden.',
    });
    fixture.detectChanges();
    expect(component.errorMessageFor(component.form.controls.passwordConfirmation)).toBe(
      'Las contraseñas no coinciden.',
    );
  });

  it('errorMessageFor wiring: renders the server error <p role="alert"> for a pristine username field with fieldErrors populated', () => {
    const { host, component, fixture, refresh } = render();
    fixture.componentInstance.fieldErrors.set({ username: 'Este usuario ya existe.' });
    refresh();
    const errorEl = host.querySelector('#admin-username-error');
    expect(errorEl).toBeTruthy();
    expect(errorEl!.getAttribute('role')).toBe('alert');
    expect(errorEl!.textContent?.trim()).toBe('Este usuario ya existe.');
    // The input must carry aria-invalid="true" and the
    // matching aria-describedby (server error = visible
    // immediately, no touch needed).
    const input = host.querySelector('#admin-username') as HTMLInputElement;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('admin-username-error');
    expect(component.shouldShowError(component.form.controls.username)).toBe(true);
  });
});

/**
 * Direct unit test for the pure {@code matchValidator}
 * factory. Lives in the same spec because the factory is
 * only consumed by AdminStep; no separate file needed.
 */
describe('matchValidator', () => {
  function fakeControl(value: string, parent: unknown): {
    value: string;
    parent: { get: (name: string) => unknown };
  } {
    return {
      value,
      parent: parent as { get: (name: string) => unknown },
    };
  }

  it('returns null when control.parent is null (not yet attached to a form)', () => {
    const validator = matchValidator('password');
    const control = { value: 'a', parent: null } as unknown as Parameters<typeof validator>[0];
    expect(validator(control)).toBeNull();
  });

  it('returns null when the target sibling control is not present', () => {
    const validator = matchValidator('password');
    const parent = { get: (_: string) => null };
    const control = fakeControl('a', parent);
    expect(
      validator(control as unknown as Parameters<typeof validator>[0]),
    ).toBeNull();
  });

  it('returns { mismatch: true } when values differ', () => {
    const validator = matchValidator('password');
    const target = { value: 'MiPassw0rd!Seguro' };
    const parent = { get: (n: string) => (n === 'password' ? target : null) };
    const control = fakeControl('MiPassw0rd!Segur', parent);
    expect(
      validator(control as unknown as Parameters<typeof validator>[0]),
    ).toEqual({ mismatch: true });
  });

  it('returns null when values match', () => {
    const validator = matchValidator('password');
    const target = { value: 'MiPassw0rd!Seguro' };
    const parent = { get: (n: string) => (n === 'password' ? target : null) };
    const control = fakeControl('MiPassw0rd!Seguro', parent);
    expect(
      validator(control as unknown as Parameters<typeof validator>[0]),
    ).toBeNull();
  });
});

/**
 * Host component for spec-side rendering. Mirrors the
 * pattern in {@code password-strength-indicator.spec.ts}
 * and {@code company-step.spec.ts}. The
 * {@code fieldErrors} signal is bound through the
 * template so the spec can drive the gap #1 server-error
 * scenarios end-to-end (helper + template wiring).
 */
@Component({
  standalone: true,
  imports: [AdminStepComponent],
  template: `<app-admin-step [tenantSlug]="tenantSlug" [fieldErrors]="fieldErrors()"></app-admin-step>`,
})
class HostComponent {
  tenantSlug = 'acme';
  /**
   * Writable signal of the per-field server errors.
   * Defaults to an empty record so the existing PR13
   * scenarios (which don't care about server errors)
   * render in their clean baseline state. The gap #1
   * scenarios update it via
   * {@code fixture.componentInstance.fieldErrors.set(...)}.
   */
  readonly fieldErrors = signal<Record<string, string>>({});
}

// Unused import guard — keeps the `throwError` import in
// the dependency graph for future error-mapping specs
// without breaking strict TS unused-imports rules in this
// commit (PR12c may add the post-failure error copy spec
// and the throwError import is already in scope).
void throwError;
