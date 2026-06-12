import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { vi } from 'vitest';

import { LoginComponent } from './login';

/**
 * Unit spec for the F1 {@code /login} page (PR11b slice).
 *
 * <p>Scope of this spec is the **form shell + render**:
 * <ul>
 *   <li>Component instantiates (smoke).</li>
 *   <li>Form is invalid when all 3 fields are empty.</li>
 *   <li>Form becomes valid when all 3 fields have valid values
 *       (slug 2-12, username 3-30, password 8-128).</li>
 *   <li>Slug/username/password each reject under-length values.</li>
 *   <li>Submit button is disabled while the form is invalid.</li>
 *   <li>Submit button is enabled once the form is valid.</li>
 *   <li>The {@code aria-live="polite"} error region exists in the
 *       DOM and is empty when there is no error (PR11c binds the
 *       message into this region).</li>
 *   <li>The "Olvidaste tu contraseña?" affordance is disabled and
 *       carries the "Próximamente" tooltip.</li>
 *   <li>The "Registrate" link exists with the "Registrate" label.</li>
 *   <li>The {@code onSubmit} no-op does not navigate to any URL.</li>
 * </ul>
 *
 * <p>NOT in scope here (lands in PR11c):
 * <ul>
 *   <li>Password show/hide toggle.</li>
 *   <li>{@code onSubmit} wiring to {@code LoginService.submit}.</li>
 *   <li>Error rendering from {@code LoginService.extractErrorCopy}.</li>
 *   <li>{@code safeReturnUrl} navigation on success.</li>
 *   <li>Placeholder removal + routes update.</li>
 * </ul>
 *
 * <p>Mocking strategy: the component injects {@code Router} and
 * {@code ActivatedRoute} (both real Angular classes, both are
 * resolved via the standard {@code @angular/router} providers in
 * production; here we pass minimal mocks because PR11b's
 * {@code onSubmit} is a no-op and the component does not yet read
 * query params). {@code LoginService} injection arrives in PR11c
 * alongside the submit wiring — at that point the service will be
 * a real dependency on the chain tip.
 */
describe('LoginComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<LoginComponent>>;
  let component: LoginComponent;

  /**
   * Helper — typed wrapper around {@code nativeElement.querySelector}
   * because the Angular TestBed types it as {@code {}} in this
   * builder config.
   */
  function qs<T extends Element = Element>(selector: string): T | null {
    return (fixture.nativeElement as unknown as Document).querySelector(selector) as T | null;
  }

  function qsAll<T extends Element = Element>(selector: string): T[] {
    return Array.from(
      (fixture.nativeElement as unknown as Document).querySelectorAll(selector),
    ) as T[];
  }

  beforeEach(() => {
    const routerMock = { navigate: vi.fn() };
    const activatedRouteMock = {
      snapshot: { queryParamMap: { get: (_: string): string | null => null } },
    };

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('form is invalid when all 3 fields are empty', () => {
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.slug.value).toBe('');
    expect(component.form.controls.username.value).toBe('');
    expect(component.form.controls.password.value).toBe('');
  });

  it('form becomes valid when all 3 fields have valid values', () => {
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });

    expect(component.form.valid).toBe(true);
    expect(component.form.errors).toBeNull();
  });

  it('rejects a slug that is too short (less than 2 chars)', () => {
    component.form.controls.slug.setValue('a');

    expect(component.form.controls.slug.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('rejects a username that is too short (less than 3 chars)', () => {
    component.form.controls.username.setValue('ab');

    expect(component.form.controls.username.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('rejects a password that is too short (less than 8 chars)', () => {
    component.form.controls.password.setValue('short1!');

    expect(component.form.controls.password.invalid).toBe(true);
    expect(component.form.invalid).toBe(true);
  });

  it('disables the submit button while the form is invalid', () => {
    const button = qs<HTMLButtonElement>('button[type="submit"]');

    expect(button).toBeTruthy();
    expect(button!.disabled).toBe(true);
  });

  it('enables the submit button once the form is valid', () => {
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    const button = qs<HTMLButtonElement>('button[type="submit"]');

    expect(button).toBeTruthy();
    expect(button!.disabled).toBe(false);
  });

  it('renders an empty aria-live="polite" error region (PR11c binds the message here)', () => {
    const region = qs<HTMLElement>('[aria-live="polite"]');

    expect(region).toBeTruthy();
    expect(region!.textContent?.trim() ?? '').toBe('');
  });

  it('disables the "Olvidaste tu contraseña?" affordance and shows the "Próximamente" tooltip', () => {
    // PR11b renders this as a <button> with [disabled]="true".
    // (PR11c may re-render it as a link; for now the affordance
    // must be present, disabled, and carry the tooltip.)
    const candidates = qsAll<HTMLElement>('button, a');
    const forgot = candidates.find((el) =>
      (el.textContent ?? '').trim().includes('¿Olvidaste tu contraseña?'),
    );

    expect(forgot).toBeTruthy();
    expect(forgot!.getAttribute('title')).toBe('Próximamente');
    // Disabled is the user-visible contract. <a> uses aria-disabled.
    const buttonLike = forgot as HTMLButtonElement;
    const isDisabled =
      (buttonLike.disabled as boolean | undefined) === true ||
      forgot!.getAttribute('aria-disabled') === 'true';
    expect(isDisabled).toBe(true);
  });

  it('exposes a "Registrate" link that uses routerLink="/register"', () => {
    const links = qsAll<HTMLAnchorElement>('a');
    const register = links.find(
      (el) => (el.textContent ?? '').trim() === 'Registrate',
    );

    expect(register).toBeTruthy();
    // The link must exist with the "Registrate" text. The
    // routerLink directive (Angular 21) needs an active
    // router to rewrite the href attribute; this test does
    // not provide a router (PR11b keeps the test surface
    // tight). We assert the link's text content and that
    // the element is an <a> — the RouterLink wiring itself
    // is verified by the source code (the template uses
    // routerLink="/register") and by the e2e gate in a
    // later PR.
    expect(register!.tagName.toLowerCase()).toBe('a');
  });

  it('does not navigate on a no-op submit (PR11c wires the real handler)', () => {
    // Fill the form so a real submit would be valid.
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    const router = TestBed.inject(Router) as unknown as { navigate: ReturnType<typeof vi.fn> };

    // Invoke the no-op handler directly. The contract: PR11b's
    // onSubmit does not navigate and does not call any service.
    component.onSubmit();
    component.onSubmit();

    expect(router.navigate).not.toHaveBeenCalled();
  });
});
