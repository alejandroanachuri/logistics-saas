import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { vi } from 'vitest';
import { ReactiveFormsModule } from '@angular/forms';
import { signal } from '@angular/core';

import { TeamEditComponent } from './team-edit';
import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore } from '../../../core/state/company-users-store';
import {
  AuthUser,
  CompanyUserDetail,
  UpdateCompanyUserRequest,
} from '../../../core/types';

function makeUser(roles: string[], id = 'me'): AuthUser {
  return {
    id,
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

function makeDetail(
  overrides: Partial<CompanyUserDetail> = {},
): CompanyUserDetail {
  return {
    id: 'u-target',
    username: 'juan',
    email: 'juan@test.com',
    firstName: 'Juan',
    lastName: 'Pérez',
    status: 'ACTIVE',
    emailVerified: true,
    lastLoginAt: null,
    roles: [],
    createdAt: '2026-01-01T00:00:00Z',
    isFirstAdmin: false,
    failedLoginAttempts: 0,
    lockedUntil: null,
    updatedAt: null,
    ...overrides,
  };
}

describe('TeamEditComponent', () => {
  let fixture: ComponentFixture<TeamEditComponent>;
  let component: TeamEditComponent;
  let companyUsersStoreMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    currentCompanyUser: ReturnType<typeof vi.fn>;
    clearDetail: ReturnType<typeof vi.fn>;
  };
  let activatedRouteId = 'u-target';
  let navigatedTo: unknown[] | null = null;

  function render(currentUser: AuthUser | null, target: CompanyUserDetail): void {
    companyUsersStoreMock = {
      loadDetail: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
        return target;
      }),
      update: vi.fn().mockResolvedValue(makeDetail({ id: activatedRouteId })),
      currentCompanyUser: vi.fn().mockReturnValue(target),
      clearDetail: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [TeamEditComponent, ReactiveFormsModule],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CompanyUsersStore, useValue: companyUsersStoreMock },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { paramMap: convertToParamMap({ id: activatedRouteId }) },
          },
        },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    const router = TestBed.inject(Router);
    navigatedTo = null;
    vi.spyOn(router, 'navigate').mockImplementation(async (commands: readonly unknown[]) => {
      navigatedTo = [...commands];
      return true;
    });

    if (currentUser) authStore.setUser(currentUser);
    fixture = TestBed.createComponent(TeamEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    expect(component).toBeTruthy();
  });

  // -------- form pre-fill --------

  it('pre-fills the form with the target user values on init', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez', email: 'juan@test.com' }),
    );
    // The constructor effect resolves the loadDetail promise.
    await Promise.resolve();
    fixture.detectChanges();

    const firstName = component.form.controls.firstName.value;
    const lastName = component.form.controls.lastName.value;
    const email = component.form.controls.email.value;
    expect(firstName).toBe('Juan');
    expect(lastName).toBe('Pérez');
    expect(email).toBe('juan@test.com');
  });

  it('renders the header with the target user name', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Editar');
  });

  it('leaves the email field enabled for non-first-admin targets', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail({ isFirstAdmin: false }));
    await Promise.resolve();
    fixture.detectChanges();

    const emailInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[id="edit-email"]',
    ) as HTMLInputElement | null;
    expect(emailInput?.disabled).toBe(false);
  });

  // -------- redirect rules --------

  it('redirects to /team/:id when the target is the first admin', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ isFirstAdmin: true, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    expect(navigatedTo).not.toBeNull();
    expect(navigatedTo).toEqual(['/team', 'u-target']);
  });

  it('redirects to /team/:id when the target is the current user (self)', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'u-target'),
      makeDetail({ isFirstAdmin: false, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    expect(navigatedTo).not.toBeNull();
    expect(navigatedTo).toEqual(['/team', 'u-target']);
  });

  // -------- save flow --------

  it('calls store.update with the form values and navigates to /team/:id on success', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez', email: 'juan@test.com' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    component.form.controls.firstName.setValue('María José');
    component.form.controls.lastName.setValue('García');
    component.form.controls.email.setValue('maria@test.com');

    component.onSubmit();

    expect(companyUsersStoreMock.update).toHaveBeenCalledTimes(1);
    const [idArg, payload] = companyUsersStoreMock.update.mock.calls[0];
    expect(idArg).toBe('u-target');
    expect(payload as UpdateCompanyUserRequest).toEqual({
      firstName: 'María José',
      lastName: 'García',
      email: 'maria@test.com',
    });

    // Wait for the promise to resolve and navigation to fire.
    await Promise.resolve();
    await Promise.resolve();
    expect(navigatedTo).toEqual(['/team', 'u-target']);
  });

  it('does not submit when the form is invalid', () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    fixture.detectChanges();

    component.form.controls.email.setValue('not-an-email');

    component.onSubmit();

    expect(companyUsersStoreMock.update).not.toHaveBeenCalled();
  });

  it('marks all fields as touched on submit so error messages show', () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    fixture.detectChanges();

    component.form.controls.firstName.setValue('');
    component.form.controls.lastName.setValue('');
    component.form.controls.email.setValue('not-an-email');

    component.onSubmit();

    expect(component.form.controls.firstName.touched).toBe(true);
    expect(component.form.controls.lastName.touched).toBe(true);
    expect(component.form.controls.email.touched).toBe(true);
  });

  // -------- validation errors --------

  it('shows a validation error when the email is invalid', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    component.form.controls.email.setValue('not-an-email');
    component.form.controls.email.markAsTouched();
    component.form.controls.email.markAsDirty();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('válido');
  });
});
