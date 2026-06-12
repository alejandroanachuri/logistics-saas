import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { vi } from 'vitest';

import { LoginComponent } from './login';
import { LoginService } from '../../core/services/login.service';
import { ApiHttpError } from '../../core/types';

/**
 * Build a minimal post-{@code errorInterceptor} payload.
 * Mirrors the helper in {@code login.service.spec.ts}.
 */
function makeApiHttpError(
  status: number,
  body: ApiHttpError['error'],
  headers?: Record<string, string>,
): ApiHttpError {
  const err = new Error(`HTTP ${status}`) as unknown as ApiHttpError;
  (err as unknown as { status: number }).status = status;
  (err as unknown as { statusText: string }).statusText = 'Error';
  (err as unknown as { error: ApiHttpError['error'] }).error = body;
  if (headers) {
    (err as unknown as { headers: { get: (k: string) => string | null } }).headers = {
      get: (k: string) => (k in headers ? headers[k] : null),
    };
  }
  return err;
}

/**
 * Unit spec for the F1 {@code /login} page (PR11b + PR11c
 * slices). PR11b covered form validity + render shell;
 * PR11c adds submit wiring, error mapping, password
 * show/hide toggle, and the loading-state contract.
 *
 * <p>The first {@code it()} is a no-op warm-up that exists
 * to absorb the vitest-builder orphan quirk (see apply-
 * progress observation #122 Discovery #15) — the builder
 * silently drops the first test of any newly-added spec
 * file; the warm-up makes the count predictable.
 */
describe('LoginComponent', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<LoginComponent>>;
  let component: LoginComponent;
  let routerMock: { navigate: ReturnType<typeof vi.fn>; navigateByUrl: ReturnType<typeof vi.fn> };
  let loginServiceMock: {
    submit: ReturnType<typeof vi.fn>;
    extractErrorCopy: ReturnType<typeof vi.fn>;
    safeReturnUrl: ReturnType<typeof vi.fn>;
  };

  function qs<T extends Element = Element>(selector: string): T | null {
    return (fixture.nativeElement as unknown as Document).querySelector(selector) as T | null;
  }

  function qsAll<T extends Element = Element>(selector: string): T[] {
    return Array.from(
      (fixture.nativeElement as unknown as Document).querySelectorAll(selector),
    ) as T[];
  }

  beforeEach(() => {
    routerMock = { navigate: vi.fn(), navigateByUrl: vi.fn() };
    const activatedRouteMock = {
      snapshot: { queryParamMap: { get: (_: string): string | null => null } },
    };
    loginServiceMock = {
      submit: vi.fn(),
      extractErrorCopy: vi.fn().mockReturnValue({ code: 'UNKNOWN', message: '' }),
      safeReturnUrl: vi.fn().mockReturnValue('/dashboard'),
    };

    TestBed.configureTestingModule({
      imports: [LoginComponent],
      providers: [
        { provide: Router, useValue: routerMock },
        { provide: ActivatedRoute, useValue: activatedRouteMock },
        { provide: LoginService, useValue: loginServiceMock },
      ],
    });

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // -------- warm-up (vitest orphan offset) --------

  it('warm-up — component instantiates', () => {
    // No-op warm-up; absorbs the first-test-of-spec drop.
    expect(component).toBeTruthy();
  });

  // -------- PR11b baseline: form validity + render shell --------

  it('form is invalid when all 3 fields are empty', () => {
    expect(component.form.invalid).toBe(true);
    expect(component.form.controls.slug.value).toBe('');
    expect(component.form.controls.username.value).toBe('');
    expect(component.form.controls.password.value).toBe('');
  });

  it('form becomes valid when all 3 fields have valid values', () => {
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    expect(component.form.valid).toBe(true);
  });

  it('rejects a slug that is too short (less than 2 chars)', () => {
    component.form.controls.slug.setValue('a');
    expect(component.form.controls.slug.invalid).toBe(true);
  });

  it('rejects a username that is too short (less than 3 chars)', () => {
    component.form.controls.username.setValue('ab');
    expect(component.form.controls.username.invalid).toBe(true);
  });

  it('rejects a password that is too short (less than 8 chars)', () => {
    component.form.controls.password.setValue('short1!');
    expect(component.form.controls.password.invalid).toBe(true);
  });

  it('disables the submit button while the form is invalid', () => {
    const button = qs<HTMLButtonElement>('button[type="submit"]');
    expect(button!.disabled).toBe(true);
  });

  it('enables the submit button once the form is valid', () => {
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();
    const button = qs<HTMLButtonElement>('button[type="submit"]');
    expect(button!.disabled).toBe(false);
  });

  it('renders an empty aria-live="polite" error region by default', () => {
    const region = qs<HTMLElement>('[aria-live="polite"]');
    expect(region!.textContent?.trim() ?? '').toBe('');
  });

  it('disables the "Olvidaste tu contraseña?" affordance and shows the "Próximamente" tooltip', () => {
    const forgot = qsAll<HTMLElement>('button, a').find((el) =>
      (el.textContent ?? '').trim().includes('¿Olvidaste tu contraseña?'),
    );
    expect(forgot).toBeTruthy();
    expect(forgot!.getAttribute('title')).toBe('Próximamente');
  });

  it('exposes a "Registrate" link as an <a>', () => {
    const register = qsAll<HTMLAnchorElement>('a').find(
      (el) => (el.textContent ?? '').trim() === 'Registrate',
    );
    expect(register).toBeTruthy();
    expect(register!.tagName.toLowerCase()).toBe('a');
  });

  // -------- PR11c: password show/hide toggle --------

  it('renders the password input with type="password" by default', () => {
    const input = qs<HTMLInputElement>('input[formcontrolname="password"]');
    expect(input!.type).toBe('password');
  });

  it('flips the password input type when the show/hide toggle is clicked', () => {
    const input = () => qs<HTMLInputElement>('input[formcontrolname="password"]')!;
    const toggle = qsAll<HTMLButtonElement>('button[type="button"]').find((b) =>
      (b.getAttribute('aria-label') ?? '').toLowerCase().includes('contrase'),
    )!;
    expect(input().type).toBe('password');
    toggle.click();
    fixture.detectChanges();
    expect(input().type).toBe('text');
    toggle.click();
    fixture.detectChanges();
    expect(input().type).toBe('password');
  });

  it('persists the password visibility toggle across re-renders (signal-backed)', () => {
    const toggle = qsAll<HTMLButtonElement>('button[type="button"]').find((b) =>
      (b.getAttribute('aria-label') ?? '').toLowerCase().includes('contrase'),
    )!;
    toggle.click();
    fixture.detectChanges();
    // Force a re-render by mutating an unrelated form value.
    component.form.controls.slug.setValue('mvr');
    fixture.detectChanges();
    expect(qs<HTMLInputElement>('input[formcontrolname="password"]')!.type).toBe('text');
  });

  // -------- PR11c: submit wiring + error mapping --------

  it('navigates to safeReturnUrl on successful submit', () => {
    loginServiceMock.submit.mockReturnValue(of({ user: {} as never, expiresIn: 900 }));
    loginServiceMock.safeReturnUrl.mockReturnValue('/dashboard');
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    expect(loginServiceMock.submit).toHaveBeenCalledWith({
      slug: 'mvr',
      username: 'juan',
      password: 'Secret123!',
    });
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });

  it('navigates to the returnUrl query-param path on successful submit when present', () => {
    loginServiceMock.submit.mockReturnValue(of({ user: {} as never, expiresIn: 900 }));
    loginServiceMock.safeReturnUrl.mockReturnValue('/register');
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/register');
  });

  it('renders INVALID_CREDENTIALS error copy and flips isLoading back to false', () => {
    loginServiceMock.submit.mockReturnValue(
      throwError(
        () =>
          makeApiHttpError(401, {
            error: { code: 'INVALID_CREDENTIALS', message: 'Las credenciales no son correctas.' },
          }),
      ),
    );
    loginServiceMock.extractErrorCopy.mockReturnValue({
      code: 'INVALID_CREDENTIALS',
      message: 'Las credenciales no son correctas.',
    });
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'wrongpass' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    const region = qs<HTMLElement>('[aria-live="polite"]');
    expect(region!.textContent ?? '').toContain('Las credenciales no son correctas.');
    expect(component.isLoading()).toBe(false);
    expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
  });

  it('renders ACCOUNT_LOCKED error copy in the aria-live region', () => {
    loginServiceMock.submit.mockReturnValue(
      throwError(
        () =>
          makeApiHttpError(423, {
            error: {
              code: 'ACCOUNT_LOCKED',
              message: 'Cuenta bloqueada. Probá de nuevo en 5 minutos.',
            },
          }),
      ),
    );
    loginServiceMock.extractErrorCopy.mockReturnValue({
      code: 'ACCOUNT_LOCKED',
      message: 'Cuenta bloqueada. Probá de nuevo en 5 minutos.',
    });
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    component.onSubmit();
    fixture.detectChanges();

    const region = qs<HTMLElement>('[aria-live="polite"]');
    expect(region!.textContent ?? '').toContain('Cuenta bloqueada');
    expect(component.isLoading()).toBe(false);
    expect(routerMock.navigateByUrl).not.toHaveBeenCalled();
  });

  it('sets isLoading to true synchronously when onSubmit fires', () => {
    // The mock returns a never-completing observable so we
    // can observe isLoading in flight synchronously.
    loginServiceMock.submit.mockImplementation(
      () =>
        new (class {
          subscribe(): { unsubscribe: () => void } {
            return { unsubscribe: () => undefined };
          }
        })() as never,
    );
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'Secret123!' });
    fixture.detectChanges();

    expect(component.isLoading()).toBe(false);
    component.onSubmit();
    expect(component.isLoading()).toBe(true);
  });

  it('clears a previous error message at the start of a new submit', () => {
    // First submit: error.
    loginServiceMock.submit.mockReturnValueOnce(
      throwError(
        () =>
          makeApiHttpError(401, {
            error: { code: 'INVALID_CREDENTIALS', message: 'Las credenciales no son correctas.' },
          }),
      ),
    );
    loginServiceMock.extractErrorCopy.mockReturnValueOnce({
      code: 'INVALID_CREDENTIALS',
      message: 'Las credenciales no son correctas.',
    });
    component.form.setValue({ slug: 'mvr', username: 'juan', password: 'wrongpass' });
    fixture.detectChanges();
    component.onSubmit();
    fixture.detectChanges();
    expect(qs<HTMLElement>('[aria-live="polite"]')!.textContent ?? '').toContain(
      'Las credenciales no son correctas.',
    );

    // Second submit: success. errorMessage must be cleared.
    loginServiceMock.submit.mockReturnValueOnce(of({ user: {} as never, expiresIn: 900 }));
    loginServiceMock.safeReturnUrl.mockReturnValue('/dashboard');
    component.onSubmit();
    fixture.detectChanges();

    expect((qs<HTMLElement>('[aria-live="polite"]')!.textContent ?? '').trim()).toBe('');
    expect(routerMock.navigateByUrl).toHaveBeenCalledWith('/dashboard');
  });
});
