import { TestBed, ComponentFixture } from '@angular/core/testing';
import { Component, Injector, runInInjectionContext } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule } from '@angular/forms';

import { ConfirmationStepComponent } from './confirmation-step';
import { CompanyFormGroup } from './company-step';
import { AdminFormGroup } from './admin-step';

/**
 * Unit spec for the F1 register wizard step 3
 * ({@code ConfirmationStepComponent}). The component
 * renders a read-only summary of the data the user entered
 * in steps 1 and 2 + 2 consent checkboxes; the step does
 * NOT have its own submit button (the stepper's
 * "Crear cuenta" button drives the submit).
 *
 * <p>The first {@code it()} is a no-op warm-up that
 * exists to absorb the vitest-builder orphan quirk (see
 * apply-progress observation #122 Discovery #15) — the
 * builder silently drops the first test of any
 * newly-added spec file; the warm-up makes the count
 * predictable.
 *
 * <p>Test count: 5 scenarios → vitest orphan drops 1 →
 * 4 reported by the runner.
 */
describe('ConfirmationStepComponent', () => {
  function makeCompanyForm(injector: Injector): CompanyFormGroup {
    return runInInjectionContext(injector, () => {
      const fb = TestBed.inject(FormBuilder);
      return fb.group({
        legalName: fb.control('Acme S.A.'),
        commercialName: fb.control('Acme'),
        cuit: fb.control('20123456780'),
        taxType: fb.control('RESPONSABLE_INSCRIPTO'),
        slug: fb.control('acme'),
        contactEmail: fb.control('contact@acme.test'),
        contactPhone: fb.control('+541112345678'),
        address: fb.group({
          country: fb.control('AR'),
          province: fb.control('AR-B'),
          city: fb.control('CABA'),
          line: fb.control('Av. Corrientes'),
          number: fb.control('1234'),
          floor: fb.control(''),
          apartment: fb.control(''),
          postalCode: fb.control('C1043'),
        }),
      }) as unknown as CompanyFormGroup;
    });
  }

  function makeAdminForm(injector: Injector): AdminFormGroup {
    return runInInjectionContext(injector, () => {
      const fb = TestBed.inject(FormBuilder);
      return fb.group({
        firstName: fb.control('Ada'),
        lastName: fb.control('Lovelace'),
        username: fb.control('ada'),
        email: fb.control('ada@acme.test'),
        password: fb.control('MiPassw0rd!Seguro'),
        passwordConfirmation: fb.control('MiPassw0rd!Seguro'),
      }) as unknown as AdminFormGroup;
    });
  }

  function render(): {
    host: HTMLElement;
    component: ConfirmationStepComponent;
    fixture: ComponentFixture<HostComponent>;
    refresh: () => void;
  } {
    const fixture: ComponentFixture<HostComponent> = TestBed.createComponent(HostComponent);
    // The host's fields are typed as `FormGroup | null` so
    // Angular's strict template type-check doesn't reject
    // the binding before the spec wires concrete values.
    // We always set them here, so the runtime value is never
    // null when the template renders.
    fixture.componentInstance.companyForm = makeCompanyForm(fixture.debugElement.injector);
    fixture.componentInstance.adminForm = makeAdminForm(fixture.debugElement.injector);
    fixture.detectChanges();
    const component = fixture.debugElement.children[0].componentInstance as ConfirmationStepComponent;
    return {
      host: fixture.nativeElement as HTMLElement,
      component,
      fixture,
      refresh: () => fixture.detectChanges(),
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ReactiveFormsModule],
    });
  });

  // -------- 1. Warm-up (vitest orphan offset) --------

  it('warm-up — component instantiates', () => {
    const { component } = render();
    expect(component).toBeTruthy();
  });

  // -------- 2. Renders the company summary from the companyForm input --------

  it('renders the company summary (legalName, commercialName, cuit, taxType, slug, contactEmail, contactPhone, address.city) from the companyForm input', () => {
    const { host } = render();
    const text = host.textContent ?? '';
    // legalName
    expect(text).toContain('Acme S.A.');
    // commercialName
    expect(text).toContain('Acme');
    // cuit
    expect(text).toContain('20123456780');
    // taxType — bound verbatim
    expect(text).toContain('RESPONSABLE_INSCRIPTO');
    // slug
    expect(text).toContain('acme');
    // contactEmail
    expect(text).toContain('contact@acme.test');
    // contactPhone
    expect(text).toContain('+541112345678');
    // address.city (1 representative address field; the
    // spec doesn't duplicate assertions for every nested
    // address control)
    expect(text).toContain('CABA');
  });

  // -------- 3. Renders the admin summary from the adminForm input (NO password) --------

  it('renders the admin summary (firstName, lastName, username, email) but NEVER the password or passwordConfirmation', () => {
    const { host } = render();
    const text = host.textContent ?? '';
    // admin fields
    expect(text).toContain('Ada');
    expect(text).toContain('Lovelace');
    expect(text).toContain('ada');
    expect(text).toContain('ada@acme.test');
    // The password value must NEVER appear in the rendered
    // summary (security: do not echo passwords back).
    expect(text).not.toContain('MiPassw0rd!Seguro');
  });

  // -------- 4. The form is invalid when both checkboxes are unchecked --------

  it('form is invalid when both consent checkboxes are unchecked', () => {
    const { component } = render();
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.acceptsTerms.value).toBe(false);
    expect(component.form.controls.acceptsPrivacy.value).toBe(false);
  });

  // -------- 5. The form is valid when both checkboxes are checked --------

  it('form is valid when both consent checkboxes are checked', () => {
    const { component } = render();
    component.form.controls.acceptsTerms.setValue(true);
    component.form.controls.acceptsPrivacy.setValue(true);
    expect(component.form.valid).toBe(true);
  });
});

// Keep `FormGroup` in the dependency graph (used by
// the host's typed form values); satisfies strict TS
// no-unused-imports without bloating the test file.
export type { FormGroup };

/**
 * Host component for spec-side rendering. Mirrors the
 * pattern in {@code admin-step.spec.ts}: drives the
 * {@code companyForm} / {@code adminForm} signal inputs
 * through Angular's normal template binding (Angular 21's
 * {@code InputSignal<T>} does not expose a public setter
 * for external callers — see PR12a Discovery #24).
 *
 * <p>The host declares the fields as nullable so the
 * Angular strict template type-check accepts the binding
 * before the spec wires concrete values. The spec always
 * assigns in {@code render()}, so the runtime value is
 * never {@code null} when the template renders.
 */
@Component({
  standalone: true,
  imports: [ConfirmationStepComponent],
  // The host declares the form fields as nullable so
  // Angular's strict template type-check accepts the
  // binding before the spec wires concrete values in
  // `render()`. The spec always assigns in `render()`,
  // so the runtime value is never `null` when the
  // template renders. The cast in the binding is the
  // minimum cost of the nullable host pattern — the
  // `input.required<FormGroup>()` on the child enforces
  // non-null at the child's API surface.
  template: `<app-confirmation-step [companyForm]="companyForm!" [adminForm]="adminForm!" />`,
})
class HostComponent {
  companyForm: CompanyFormGroup | null = null;
  adminForm: AdminFormGroup | null = null;
}
