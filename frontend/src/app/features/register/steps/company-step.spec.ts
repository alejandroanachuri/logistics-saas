import { TestBed } from '@angular/core/testing';
import { ComponentFixture } from '@angular/core/testing';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import { vi } from 'vitest';

import {
  CompanyStepComponent,
  cuitIsValid,
  cuitMod11Validator,
} from './company-step';
import { ProvincesService } from '../../../core/services/provinces.service';
import { AvailabilityService } from '../../../core/services/availability.service';

/**
 * Host that wraps {@code CompanyStepComponent} so the spec
 * can drive the template through Angular's normal rendering
 * pipeline (the province {@code <select>}, the Siguiente
 * button, the error region, the tax type select). The
 * component does NOT expose a signal-input API; the host
 * pattern keeps the rendered DOM observable from the spec
 * without poking private state.
 *
 * <p>The first {@code it()} is a no-op warm-up that exists
 * to absorb the vitest-builder orphan quirk (see apply-
 * progress observation #122 Discovery #15) — the builder
 * silently drops the first test of any newly-added spec file;
 * the warm-up makes the count predictable.
 *
 * <p>Test count: 13 scenarios → vitest orphan drops 1 →
 * 12 reported by the runner. After the PR12a tip baseline
 * of 84 tests + 12 spec files, this brings the totals to
 * 96 tests / 13 spec files.
 */
describe('CompanyStepComponent', () => {
  const SAMPLE_PROVINCES = [
    { code: 'BUENOS_AIRES', displayName: 'Buenos Aires' },
    { code: 'CABA', displayName: 'Ciudad Autónoma de Buenos Aires' },
    { code: 'CORDOBA', displayName: 'Córdoba' },
  ];

  let availabilityMock: {
    checkSlug: ReturnType<typeof vi.fn>;
    checkCuit: ReturnType<typeof vi.fn>;
    checkUsername: ReturnType<typeof vi.fn>;
  };

  function render(): {
    host: HTMLElement;
    component: CompanyStepComponent;
    refresh: () => void;
  } {
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as CompanyStepComponent;
    return {
      host: fixture.nativeElement as HTMLElement,
      component,
      refresh: () => fixture.detectChanges(),
    };
  }

  /** Drive a form patch with values that satisfy every sync validator. */
  function patchValidCompany(component: CompanyStepComponent): void {
    component.form.patchValue({
      legalName: 'Acme SA',
      cuit: '30123456781',
      taxType: 'RESPONSABLE_INSCRIPTO',
      slug: 'acme',
      contactEmail: 'ops@acme.example',
      contactPhone: '+54 11 5555-0000',
      address: {
        country: 'AR',
        province: 'BUENOS_AIRES',
        city: 'CABA',
        line: 'Av Corrientes',
        number: '1234',
        postalCode: 'C1043',
      },
    });
  }

  beforeEach(() => {
    availabilityMock = {
      checkSlug: vi.fn().mockReturnValue(of({ available: true })),
      checkCuit: vi.fn().mockReturnValue(of({ available: true })),
      checkUsername: vi.fn().mockReturnValue(of({ available: true })),
    };
    TestBed.configureTestingModule({
      providers: [
        { provide: ProvincesService, useValue: { list: () => of(SAMPLE_PROVINCES) } },
        { provide: AvailabilityService, useValue: availabilityMock },
      ],
    });
  });

  // 1. Warm-up (vitest orphan offset).

  it('warm-up — component instantiates', () => {
    expect(true).toBe(true);
  });

  // 2. Form is invalid when all 15 controls are empty.

  it('form is invalid when all controls are empty and the address defaults are seeded', () => {
    const { component } = render();
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.address.controls.province.value).toBe('BUENOS_AIRES');
    expect(component.form.controls.address.controls.country.value).toBe('AR');
  });

  // 3. legalName required + minLength 2 + maxLength 80.

  it('legalName: required; rejects empty and 1-char; accepts 2-80 chars', () => {
    const { component } = render();
    component.form.controls.legalName.setValue('');
    expect(component.form.controls.legalName.invalid).toBe(true);
    component.form.controls.legalName.setValue('A');
    expect(component.form.controls.legalName.invalid).toBe(true);
    component.form.controls.legalName.setValue('Acme');
    expect(component.form.controls.legalName.errors).toBeNull();
  });

  // 4. cuit pattern (11 digits, no dashes) + mod-11. The pure helpers
  //    cuitIsValid + cuitMod11Validator are also exercised here.

  it('cuit: rejects non-digits and wrong length; mod-11 accepts 30123456781 and rejects 30123456780', () => {
    const { component } = render();
    // Pattern: 11 digits, no dashes.
    component.form.controls.cuit.setValue('30-12345678-1');
    expect(component.form.controls.cuit.invalid).toBe(true);
    component.form.controls.cuit.setValue('3012345678');
    expect(component.form.controls.cuit.invalid).toBe(true);
    // mod-11: 30123456781 is valid; 30123456780 is bad.
    expect(cuitIsValid('30123456781')).toBe(true);
    expect(cuitIsValid('30123456780')).toBe(false);
    const validator = cuitMod11Validator();
    const badControl = { value: '30123456780' } as unknown as Parameters<typeof validator>[0];
    expect(validator(badControl)).toEqual({ cuit: { valid: false } });
    component.form.controls.cuit.setValue('30123456781');
    expect(component.form.controls.cuit.errors).toBeNull();
  });

  // 5. slug pattern ^[a-z][a-z0-9]{1,11}$ — uppercase, leading digit,
  //    too short, too long all rejected; a valid slug has no sync errors
  //    (the async availability validator stays pending, so we assert
  //    `errors === null`, not `valid` which is gated on `!pending`).

  it('slug pattern: rejects uppercase, leading digit, < 2 or > 12 chars; accepts a valid slug', () => {
    const { component } = render();
    component.form.controls.slug.setValue('Mvr');
    expect(component.form.controls.slug.invalid).toBe(true);
    component.form.controls.slug.setValue('1mvr');
    expect(component.form.controls.slug.invalid).toBe(true);
    component.form.controls.slug.setValue('m');
    expect(component.form.controls.slug.invalid).toBe(true);
    component.form.controls.slug.setValue('mvr-logistics-solutions-inc');
    expect(component.form.controls.slug.invalid).toBe(true);
    component.form.controls.slug.setValue('mvr');
    expect(component.form.controls.slug.errors).toBeNull();
  });

  // 6. slug async validator debounces 300ms.

  it('slug async validator debounces 300ms and issues one call after the last value', async () => {
    const { component } = render();
    component.form.controls.slug.setValue('m');
    await new Promise((r) => setTimeout(r, 30));
    component.form.controls.slug.setValue('mv');
    await new Promise((r) => setTimeout(r, 30));
    component.form.controls.slug.setValue('mvr');
    await new Promise((r) => setTimeout(r, 400));

    expect(availabilityMock.checkSlug).toHaveBeenCalledTimes(1);
    expect(availabilityMock.checkSlug).toHaveBeenCalledWith('mvr');
  });

  // 7. slug async returns { slugTaken: true } on unavailable.

  it('slug async validator returns { slugTaken: true } when the API says unavailable', async () => {
    availabilityMock.checkSlug.mockReturnValue(of({ available: false, reason: 'SLUG_ALREADY_TAKEN' }));
    const { component } = render();
    component.form.controls.slug.setValue('taken');
    await new Promise((r) => setTimeout(r, 400));
    expect(component.form.controls.slug.errors).toEqual({ slugTaken: true });
  });

  // 8. cuit async returns { cuitTaken: true } on unavailable.

  it('cuit async validator returns { cuitTaken: true } when the API says unavailable', async () => {
    availabilityMock.checkCuit.mockReturnValue(of({ available: false, reason: 'CUIT_ALREADY_REGISTERED' }));
    const { component } = render();
    component.form.controls.cuit.setValue('30123456781');
    await new Promise((r) => setTimeout(r, 400));
    expect(component.form.controls.cuit.errors).toEqual({ cuitTaken: true });
  });

  // 9. Province <select> renders one <option> per cached province.

  it('province <select> renders one <option> per province in the cached list', () => {
    const { host } = render();
    const select = host.querySelector('select[data-testid="company-province"]');
    expect(select).toBeTruthy();
    const options = Array.from(select!.querySelectorAll('option'));
    expect(options.length).toBe(3);
    const values = options.map((o) => (o as HTMLOptionElement).value);
    expect(values).toEqual(['BUENOS_AIRES', 'CABA', 'CORDOBA']);
    const labels = options.map((o) => o.textContent?.trim() ?? '');
    expect(labels).toContain('Buenos Aires');
    expect(labels).toContain('Córdoba');
  });

  // 10. Tax type <select> has 3 hardcoded options.

  it('tax type <select> renders the 3 hardcoded options', () => {
    const { host } = render();
    const select = host.querySelector('select[data-testid="company-tax-type"]');
    expect(select).toBeTruthy();
    const options = Array.from(select!.querySelectorAll('option'));
    const labels = options.map((o) => o.textContent?.trim() ?? '');
    expect(labels).toEqual(['Responsable Inscripto', 'Monotributo', 'Exento']);
  });

  // 11. Siguiente disabled when form invalid.

  it('Siguiente button is disabled while the form is invalid', () => {
    const { host } = render();
    const button = host.querySelector('button[data-testid="company-next"]') as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  // 12. Siguiente disabled while async pending (sync passes).

  it('Siguiente button is disabled when sync validators pass but async validators are pending', () => {
    const { host, component, refresh } = render();
    patchValidCompany(component);
    refresh();
    const button = host.querySelector('button[data-testid="company-next"]') as HTMLButtonElement;
    expect(component.form.pending).toBe(true);
    expect(button.disabled).toBe(true);
  });

  // 13. Siguiente enabled when form valid + not pending (after 300ms debounce).

  it('Siguiente button is enabled when the form is valid and not pending', async () => {
    const { host, component, refresh } = render();
    patchValidCompany(component);
    refresh();
    await new Promise((r) => setTimeout(r, 400));
    refresh();
    const button = host.querySelector('button[data-testid="company-next"]') as HTMLButtonElement;
    expect(component.form.pending).toBe(false);
    expect(component.form.valid).toBe(true);
    expect(button.disabled).toBe(false);
  });

  // -------- PR13 (per-field error copy) --------
  //
  // The 4 scenarios below cover the new `errorMessageFor`
  // helper. The helper must be a pure function of
  // (control, touched, errors) — no template coupling, no
  // side effects. The 5th scenario drives the template:
  // it proves the @if-error block is wired to the helper.

  it('errorMessageFor: returns null when the control has no errors (or is untouched)', () => {
    const { component } = render();
    // No errors + untouched → null (no message shown on first render).
    expect(component.errorMessageFor(component.form.controls.legalName)).toBeNull();
    // Make it touched but still no errors → null (clean value, no message).
    component.form.controls.legalName.setValue('Acme');
    component.form.controls.legalName.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.legalName)).toBeNull();
  });

  it('errorMessageFor: returns "Este campo es obligatorio." for required + touched', () => {
    const { component } = render();
    component.form.controls.legalName.setValue('');
    component.form.controls.legalName.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.legalName)).toBe(
      'Este campo es obligatorio.',
    );
  });

  it('errorMessageFor: returns "CUIT inválido. Verificá el dígito verificador." for bad mod-11', () => {
    const { component } = render();
    component.form.controls.cuit.setValue('30123456780'); // bad check digit
    component.form.controls.cuit.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.cuit)).toBe(
      'CUIT inválido. Verificá el dígito verificador.',
    );
  });

  it('errorMessageFor: returns "Este slug ya está en uso." for async slugTaken + touched', async () => {
    availabilityMock.checkSlug.mockReturnValue(
      of({ available: false, reason: 'SLUG_ALREADY_TAKEN' }),
    );
    const { component } = render();
    component.form.controls.slug.setValue('taken');
    await new Promise((r) => setTimeout(r, 400));
    component.form.controls.slug.markAsTouched();
    expect(component.errorMessageFor(component.form.controls.slug)).toBe(
      'Este slug ya está en uso.',
    );
  });

  it('errorMessageFor wiring: renders the per-field error <p role="alert"> when legalName is touched empty', () => {
    const { host, component, refresh } = render();
    component.form.controls.legalName.setValue('');
    component.form.controls.legalName.markAsTouched();
    refresh();
    const errorEl = host.querySelector('#company-legal-name-error');
    expect(errorEl).toBeTruthy();
    expect(errorEl!.getAttribute('role')).toBe('alert');
    expect(errorEl!.textContent?.trim()).toBe('Este campo es obligatorio.');
    // The input must carry aria-invalid="true" and the
    // matching aria-describedby so AT pairs them up.
    const input = host.querySelector('#company-legal-name') as HTMLInputElement;
    expect(input.getAttribute('aria-invalid')).toBe('true');
    expect(input.getAttribute('aria-describedby')).toBe('company-legal-name-error');
  });

  // -------- PR13 (required-field asterisks) --------

  it('required-field wiring: legalName input has aria-required="true" and label has the * marker', () => {
    const { host } = render();
    const input = host.querySelector('#company-legal-name') as HTMLInputElement;
    expect(input.getAttribute('aria-required')).toBe('true');
    // The label is associated via for/id; the asterisk
    // lives inside the <label> and is hidden from AT.
    const label = host.querySelector('label[for="company-legal-name"]') as HTMLLabelElement;
    const asterisk = label.querySelector('span[aria-hidden="true"]');
    expect(asterisk).toBeTruthy();
    expect(asterisk!.textContent?.trim()).toBe('*');
  });

  it('required-field wiring: commercialName input is OPTIONAL — no aria-required, no asterisk', () => {
    const { host } = render();
    const input = host.querySelector('#company-commercial-name') as HTMLInputElement;
    expect(input.hasAttribute('aria-required')).toBe(false);
    const label = host.querySelector('label[for="company-commercial-name"]') as HTMLLabelElement;
    expect(label.querySelector('span[aria-hidden="true"]')).toBeNull();
  });
});

/**
 * Host component for spec-side rendering. Mirrors the
 * pattern in {@code password-strength-indicator.spec.ts}.
 */
@Component({
  standalone: true,
  imports: [CompanyStepComponent],
  template: `<app-company-step></app-company-step>`,
})
class HostComponent {}
