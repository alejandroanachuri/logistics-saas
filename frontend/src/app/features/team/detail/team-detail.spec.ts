import { TestBed, ComponentFixture } from '@angular/core/testing';
import {
  provideRouter,
  ActivatedRoute,
  convertToParamMap,
} from '@angular/router';
import { vi } from 'vitest';

import { TeamDetailComponent } from './team-detail';
import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore } from '../../../core/state/company-users-store';
import {
  AuthUser,
  CompanyUserDetail,
  ResetPasswordResponse,
  Role,
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

function makeRole(id: string, name: string): Role {
  return { id, name, description: null };
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
    lastLoginAt: '2026-01-15T10:30:00Z',
    roles: [makeRole('r-admin', 'COMPANY_ADMIN')],
    createdAt: '2026-01-01T00:00:00Z',
    isFirstAdmin: false,
    failedLoginAttempts: 0,
    lockedUntil: null,
    updatedAt: '2026-01-15T10:30:00Z',
    ...overrides,
  };
}

describe('TeamDetailComponent', () => {
  let fixture: ComponentFixture<TeamDetailComponent>;
  let component: TeamDetailComponent;
  let companyUsersStoreMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    resetPassword: ReturnType<typeof vi.fn>;
    disable: ReturnType<typeof vi.fn>;
    reactivate: ReturnType<typeof vi.fn>;
    clearDetail: ReturnType<typeof vi.fn>;
  };
  let activatedRouteId = 'u-target';

  function render(
    currentUser: AuthUser | null,
    detail: CompanyUserDetail,
  ): void {
    companyUsersStoreMock = {
      loadDetail: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
        return detail;
      }),
      resetPassword: vi.fn(),
      disable: vi.fn().mockResolvedValue(undefined),
      reactivate: vi.fn(),
      clearDetail: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [TeamDetailComponent],
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
    if (currentUser) authStore.setUser(currentUser);
    fixture = TestBed.createComponent(TeamDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // -------- header & identity --------

  it('renders the H1 with the user full name', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Juan');
    expect(h1?.textContent).toContain('Pérez');
  });

  it('falls back to username when first/last name are missing', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ firstName: null, lastName: null, username: 'juan' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('juan');
  });

  // -------- status badge + role chips --------

  it('renders the status badge with ACTIVE status', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'ACTIVE' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      'app-status-badge [data-status]',
    );
    expect(badge).toBeTruthy();
    expect(badge?.getAttribute('data-status')).toBe('ACTIVE');
  });

  it('renders the status badge with DISABLED status', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'DISABLED' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      'app-status-badge [data-status]',
    );
    expect(badge).toBeTruthy();
    expect(badge?.getAttribute('data-status')).toBe('DISABLED');
  });

  it('renders one role chip per role in the detail', async () => {
    const roles = [
      makeRole('r-admin', 'COMPANY_ADMIN'),
      makeRole('r-operator', 'COMPANY_OPERATOR'),
    ];
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail({ roles }));
    await Promise.resolve();
    fixture.detectChanges();

    const chips = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'app-role-chip [data-role]',
    );
    expect(chips.length).toBe(2);
    expect(chips[0].getAttribute('data-role')).toBe('COMPANY_ADMIN');
    expect(chips[1].getAttribute('data-role')).toBe('COMPANY_OPERATOR');
  });

  // -------- tab switching --------

  it('starts on the info tab and switches to roles when Roles tab clicked', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const rolesTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-roles-button"]',
    ) as HTMLButtonElement | null;
    expect(rolesTab).toBeTruthy();
    rolesTab?.click();
    fixture.detectChanges();

    const rolesSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-roles"]',
    );
    expect(rolesSection).toBeTruthy();
  });

  it('switches to the security tab when clicked', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const securityTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-security-button"]',
    ) as HTMLButtonElement | null;
    securityTab?.click();
    fixture.detectChanges();

    const securitySection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-security"]',
    );
    expect(securitySection).toBeTruthy();
  });

  // -------- protected-target rules --------

  it('disables "Editar información" when the target is the first admin', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ isFirstAdmin: true, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const editInfo = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="edit-info-button"]',
    ) as HTMLButtonElement | null;
    expect(editInfo).toBeTruthy();
    expect(editInfo?.disabled).toBe(true);
  });

  it('disables "Editar información" when the target is the current user (self)', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'u-target'),
      makeDetail({ isFirstAdmin: false, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const editInfo = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="edit-info-button"]',
    ) as HTMLButtonElement | null;
    expect(editInfo?.disabled).toBe(true);
  });

  it('disables "Desactivar usuario" when the target is the current user (self)', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'u-target'),
      makeDetail({ isFirstAdmin: false, id: 'u-target', status: 'ACTIVE' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const securityTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-security-button"]',
    ) as HTMLButtonElement | null;
    securityTab?.click();
    fixture.detectChanges();

    const disableBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="disable-user-button"]',
    ) as HTMLButtonElement | null;
    expect(disableBtn?.disabled).toBe(true);
  });

  // -------- password reset flow --------

  it('opens the password reveal modal after resetPassword resolves', async () => {
    const resetResponse: ResetPasswordResponse = {
      userId: 'u-target',
      username: 'juan',
      temporaryPassword: 'Temp-Pass-1234',
      passwordWarning: 'Guardala en un canal seguro.',
    };
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    // Override the mock after render (TestBed captured the
    // reference via useValue, but the mock object itself is
    // shared so reassigning its `resetPassword` method
    // propagates to the component's inject).
    companyUsersStoreMock.resetPassword = vi
      .fn()
      .mockResolvedValue(resetResponse);

    await Promise.resolve();
    fixture.detectChanges();

    await component.resetPassword();
    await Promise.resolve();
    fixture.detectChanges();

    const modal = (fixture.nativeElement as HTMLElement).querySelector(
      'app-password-reveal-modal',
    );
    expect(modal).toBeTruthy();
    expect(modal?.textContent).toContain('juan');
    expect(modal?.textContent).toContain('Temp-Pass-1234');
  });

  it('closes the password modal when dismiss is emitted', async () => {
    const resetResponse: ResetPasswordResponse = {
      userId: 'u-target',
      username: 'juan',
      temporaryPassword: 'Temp-Pass-1234',
      passwordWarning: 'Guardala en un canal seguro.',
    };
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    companyUsersStoreMock.resetPassword = vi
      .fn()
      .mockResolvedValue(resetResponse);

    await Promise.resolve();
    fixture.detectChanges();

    await component.resetPassword();
    await Promise.resolve();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        'app-password-reveal-modal',
      ),
    ).toBeTruthy();

    component.onPasswordModalDismiss();
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector(
        'app-password-reveal-modal',
      ),
    ).toBeNull();
  });

  // -------- disable flow --------

  it('shows the disable confirm dialog when "Desactivar usuario" is clicked', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'ACTIVE', isFirstAdmin: false, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const securityTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-security-button"]',
    ) as HTMLButtonElement | null;
    securityTab?.click();
    fixture.detectChanges();

    const disableBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="disable-user-button"]',
    ) as HTMLButtonElement | null;
    disableBtn?.click();
    fixture.detectChanges();

    const dialog = (fixture.nativeElement as HTMLElement).querySelector(
      'app-confirm-dialog',
    );
    expect(dialog).toBeTruthy();
  });

  it('calls store.disable when the disable confirm dialog is confirmed', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'ACTIVE', isFirstAdmin: false, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    component.showDisableConfirm.set(true);
    fixture.detectChanges();

    await component.disable();
    await Promise.resolve();

    expect(companyUsersStoreMock.disable).toHaveBeenCalledWith('u-target');
  });

  // -------- security tab: reactivate button --------

  it('shows the "Reactivar usuario" button when status is DISABLED', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'DISABLED', isFirstAdmin: false, id: 'u-target' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const securityTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-security-button"]',
    ) as HTMLButtonElement | null;
    securityTab?.click();
    fixture.detectChanges();

    const reactivateBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="reactivate-user-button"]',
    ) as HTMLButtonElement | null;
    expect(reactivateBtn).toBeTruthy();
    expect(reactivateBtn?.disabled).toBe(false);
  });
});
