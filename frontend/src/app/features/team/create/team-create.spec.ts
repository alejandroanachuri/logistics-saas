import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { TeamCreateComponent } from './team-create';
import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore } from '../../../core/state/company-users-store';
import { RolesService } from '../../../core/services/roles.service';
import {
  AuthUser,
  CreateCompanyUserRequest,
  CreateCompanyUserResponse,
  Role,
} from '../../../core/types';

function makeUser(roles: string[]): AuthUser {
  return {
    id: 'u-admin',
    tenantId: 't1',
    tenantSlug: 'mvr',
    username: 'admin',
    email: 'admin@test.com',
    firstName: 'Admin',
    lastName: 'User',
    role: roles[0] ?? '',
    roles,
    scope: 'COMPANY',
    emailVerified: true,
  };
}

function makeRole(id: string, name: string): Role {
  return { id, name, description: null };
}

const SAMPLE_ROLES: Role[] = [
  makeRole('r-admin', 'COMPANY_ADMIN'),
  makeRole('r-operator', 'COMPANY_OPERATOR'),
  makeRole('r-viewer', 'COMPANY_VIEWER'),
];

function makeCreateResponse(
  overrides: Partial<CreateCompanyUserResponse> = {},
): CreateCompanyUserResponse {
  return {
    user: {
      id: 'u-new',
      username: 'nuevo',
      email: 'nuevo@test.com',
      firstName: 'Nuevo',
      lastName: 'User',
      status: 'ACTIVE',
      emailVerified: false,
      lastLoginAt: null,
      roles: [makeRole('r-operator', 'COMPANY_OPERATOR')],
      createdAt: '2026-06-21T00:00:00Z',
      isFirstAdmin: false,
      failedLoginAttempts: 0,
      lockedUntil: null,
      updatedAt: null,
    },
    temporaryPassword: 'TmpX9kqR!2vL',
    passwordWarning: 'Compartila por un canal seguro.',
    ...overrides,
  };
}

describe('TeamCreateComponent', () => {
  let fixture: ComponentFixture<TeamCreateComponent>;
  let component: TeamCreateComponent;
  let companyUsersStoreMock: {
    create: ReturnType<typeof vi.fn>;
    loadRoles: ReturnType<typeof vi.fn>;
  };
  let rolesServiceMock: {
    listCompanyRoles: ReturnType<typeof vi.fn>;
  };
  let navigatedTo: unknown[] | null = null;

  function render(currentUserRoles: string[] | null): void {
    companyUsersStoreMock = {
      create: vi.fn().mockResolvedValue(makeCreateResponse()),
      loadRoles: vi.fn().mockResolvedValue(undefined),
    };
    rolesServiceMock = {
      listCompanyRoles: vi.fn().mockReturnValue({
        subscribe: (observer: {
          next?: (r: Role[]) => void;
          error?: (e: unknown) => void;
        }) => {
          if (observer.next) observer.next(SAMPLE_ROLES);
          return { unsubscribe: () => undefined };
        },
      }),
    };

    TestBed.configureTestingModule({
      imports: [TeamCreateComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CompanyUsersStore, useValue: companyUsersStoreMock },
        { provide: RolesService, useValue: rolesServiceMock },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    const router = TestBed.inject(Router);
    navigatedTo = null;
    vi.spyOn(router, 'navigate').mockImplementation(async (commands: readonly unknown[]) => {
      navigatedTo = [...commands];
      return true;
    });

    if (currentUserRoles !== null) {
      authStore.setUser(makeUser(currentUserRoles));
    }
    fixture = TestBed.createComponent(TeamCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  function fillValidForm(roleId = 'r-operator'): void {
    component.form.controls['firstName']!.setValue('María');
    component.form.controls['lastName']!.setValue('García');
    component.form.controls['email']!.setValue('maria@test.com');
    component.form.controls['username']!.setValue('maria.garcia');
    component.form.controls['password']!.setValue('X9kqR!2vLmN');
    component.form.controls['passwordConfirmation']!.setValue('X9kqR!2vLmN');
    component.onRolesChange([roleId]);
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(['COMPANY_ADMIN']);
    expect(component).toBeTruthy();
  });

  // -------- header + sections --------

  it('renders the H1 "Nuevo usuario"', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Nuevo usuario');
  });

  it('renders the three section headings (Datos personales, Contraseña temporal, Roles)', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const headings = (fixture.nativeElement as HTMLElement).querySelectorAll('h2');
    const text = Array.from(headings).map((h) => h.textContent?.trim() ?? '').join(' | ');
    expect(text).toContain('Datos personales');
    expect(text).toContain('Contraseña temporal');
    expect(text).toContain('Roles');
  });

  it('renders inputs for firstName, lastName, email, username, password, passwordConfirmation', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const inputs = (fixture.nativeElement as HTMLElement).querySelectorAll('input');
    const names = Array.from(inputs).map((i) => (i as HTMLInputElement).name);
    expect(names).toContain('firstName');
    expect(names).toContain('lastName');
    expect(names).toContain('email');
    expect(names).toContain('username');
    expect(names).toContain('password');
    expect(names).toContain('passwordConfirmation');
  });

  // -------- roles load --------

  it('loads the COMPANY role catalog on init via RolesService', () => {
    render(['COMPANY_ADMIN']);
    expect(rolesServiceMock.listCompanyRoles).toHaveBeenCalledTimes(1);
    expect(component.availableRoles().length).toBe(3);
  });

  // -------- submit button enable/disable --------

  it('disables the submit button when the form is invalid (empty)', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const submit = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="submit-btn"]',
    ) as HTMLButtonElement | null;
    expect(submit?.disabled).toBe(true);
  });

  it('enables the submit button when the form is valid (password, email, username, roles all set)', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    fillValidForm();
    fixture.detectChanges();
    const submit = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="submit-btn"]',
    ) as HTMLButtonElement | null;
    expect(submit?.disabled).toBe(false);
  });

  // -------- validators --------

  it('passwordComplexityValidator rejects passwords missing upper, lower, or digit', () => {
    render(['COMPANY_ADMIN']);
    // Missing upper + digit.
    component.form.controls['password']!.setValue('lowercase1');
    component.form.controls['password']!.markAsTouched();
    expect(component.form.controls['password']!.errors?.['complexity']).toBeTruthy();

    // Missing lower + digit.
    component.form.controls['password']!.setValue('UPPERCASE1');
    component.form.controls['password']!.markAsTouched();
    expect(component.form.controls['password']!.errors?.['complexity']).toBeTruthy();

    // Missing digit.
    component.form.controls['password']!.setValue('NoDigitsHere');
    component.form.controls['password']!.markAsTouched();
    expect(component.form.controls['password']!.errors?.['complexity']).toBeTruthy();

    // All four classes present — passes complexity.
    component.form.controls['password']!.setValue('X9kqR!2vLmN');
    component.form.controls['password']!.markAsTouched();
    expect(component.form.controls['password']!.errors?.['complexity']).toBeFalsy();
  });

  it('matchPasswordValidator rejects when password and confirmation differ', () => {
    render(['COMPANY_ADMIN']);
    component.form.controls['password']!.setValue('X9kqR!2vLmN');
    component.form.controls['passwordConfirmation']!.setValue('DifferentPass1');
    component.form.controls['passwordConfirmation']!.markAsTouched();
    expect(component.form.errors?.['passwordMismatch']).toBeTruthy();
  });

  it('atLeastOneRoleValidator rejects an empty roleIds array', () => {
    render(['COMPANY_ADMIN']);
    // The roleIds control must be invalid when empty (and after touch).
    component.onRolesChange([]);
    component.form.controls['roleIds']!.markAsTouched();
    expect(component.form.controls['roleIds']!.errors?.['required']).toBeTruthy();
  });

  // -------- generate password --------

  it('"Generar contraseña aleatoria" populates both password and passwordConfirmation', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="generate-password-btn"]',
    ) as HTMLButtonElement | null;
    expect(btn).toBeTruthy();
    btn?.click();
    fixture.detectChanges();

    const password = component.form.controls['password']!.value;
    const confirmation = component.form.controls['passwordConfirmation']!.value;
    expect(typeof password).toBe('string');
    expect(password.length).toBeGreaterThanOrEqual(12);
    expect(password).toBe(confirmation);
    // 4 character classes: upper, lower, digit, symbol.
    expect(/[A-Z]/.test(password as string)).toBe(true);
    expect(/[a-z]/.test(password as string)).toBe(true);
    expect(/\d/.test(password as string)).toBe(true);
    expect(/[!@#$%^&*]/.test(password as string)).toBe(true);
  });

  // -------- successful create + modal flow --------

  it('on successful create, opens the password reveal modal with the response data', async () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    fillValidForm();

    component.onSubmit();
    // Drain the microtask queue so the .then() callback in
    // onSubmit fires before assertions.
    await Promise.resolve();
    await Promise.resolve();

    expect(companyUsersStoreMock.create).toHaveBeenCalledTimes(1);
    const payload = companyUsersStoreMock.create.mock.calls[0][0] as CreateCompanyUserRequest;
    expect(payload.username).toBe('maria.garcia');
    expect(payload.email).toBe('maria@test.com');
    expect(payload.firstName).toBe('María');
    expect(payload.lastName).toBe('García');
    expect(payload.password).toBe('X9kqR!2vLmN');
    expect(payload.roleIds).toEqual(['r-operator']);

    expect(component.showPasswordModal()).toBe(true);
    const data = component.passwordModalData();
    expect(data).not.toBeNull();
    expect(data?.temporaryPassword).toBe('TmpX9kqR!2vL');
    expect(data?.username).toBe('nuevo');
    expect(data?.warning).toBe('Compartila por un canal seguro.');
  });

  it('does not call store.create when the form is invalid', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    // Leave the form empty.
    component.onSubmit();
    expect(companyUsersStoreMock.create).not.toHaveBeenCalled();
  });

  it('marks all controls as touched when submitting an invalid form', () => {
    render(['COMPANY_ADMIN']);
    component.onSubmit();
    expect(component.form.controls['email']!.touched).toBe(true);
    expect(component.form.controls['username']!.touched).toBe(true);
    expect(component.form.controls['password']!.touched).toBe(true);
    expect(component.form.controls['passwordConfirmation']!.touched).toBe(true);
    expect(component.form.controls['roleIds']!.touched).toBe(true);
  });

  it('renders the password reveal modal after a successful create', async () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    fillValidForm();
    component.onSubmit();
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    const modal = (fixture.nativeElement as HTMLElement).querySelector(
      'app-password-reveal-modal',
    );
    expect(modal).toBeTruthy();
  });

  // -------- modal dismiss / create another --------

  it('on modal dismiss, navigates to /team', async () => {
    render(['COMPANY_ADMIN']);
    fillValidForm();
    component.onSubmit();
    await Promise.resolve();
    await Promise.resolve();
    expect(component.showPasswordModal()).toBe(true);

    component.onPasswordModalDismiss();

    expect(component.showPasswordModal()).toBe(false);
    expect(navigatedTo).toEqual(['/team']);
  });

  it('on modal "createAnother", closes the modal, clears data, and resets the form', async () => {
    render(['COMPANY_ADMIN']);
    fillValidForm();
    component.onSubmit();
    await Promise.resolve();
    await Promise.resolve();
    expect(component.showPasswordModal()).toBe(true);
    expect(component.passwordModalData()).not.toBeNull();

    component.onPasswordModalCreateAnother();

    expect(component.showPasswordModal()).toBe(false);
    expect(component.passwordModalData()).toBeNull();
    // Form was reset.
    expect(component.form.controls['firstName']!.value).toBeNull();
    expect(component.form.controls['email']!.value).toBeNull();
    expect(component.form.controls['username']!.value).toBeNull();
    expect(component.form.controls['password']!.value).toBeNull();
    expect(component.form.controls['passwordConfirmation']!.value).toBeNull();
  });
});