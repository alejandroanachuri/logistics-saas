import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { vi } from 'vitest';

import { TeamListComponent } from './team-list';
import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore, ListFilters } from '../../../core/state/company-users-store';
import { AuthUser, CompanyUserSummary, PageResponse, Role } from '../../../core/types';

function userWithRoles(roles: string[]): AuthUser {
  return {
    id: 'u1',
    tenantId: 't1',
    tenantSlug: 'mvr',
    username: 'juan',
    email: 'juan@test.com',
    firstName: 'Juan',
    lastName: 'Pérez',
    role: roles[0] ?? '',
    roles,
    scope: 'COMPANY',
    emailVerified: true,
  };
}

function makeSummary(id: string, username: string, status: 'ACTIVE' | 'DISABLED' = 'ACTIVE'): CompanyUserSummary {
  return {
    id,
    username,
    email: `${username}@test.com`,
    firstName: 'Test',
    lastName: 'User',
    status,
    emailVerified: true,
    lastLoginAt: null,
    roles: [],
    createdAt: '2026-01-01T00:00:00Z',
    isFirstAdmin: false,
  };
}

function makePage(users: CompanyUserSummary[], page = 1, total = users.length): PageResponse<CompanyUserSummary> {
  return { data: users, total, page, size: 10 };
}

const SAMPLE_ROLES: Role[] = [
  { id: 'r-admin', name: 'COMPANY_ADMIN', description: null },
  { id: 'r-operator', name: 'COMPANY_OPERATOR', description: null },
  { id: 'r-viewer', name: 'COMPANY_VIEWER', description: null },
];

describe('TeamListComponent', () => {
  let fixture: ComponentFixture<TeamListComponent>;
  let component: TeamListComponent;
  let companyUsersStoreMock: {
    loadList: ReturnType<typeof vi.fn>;
    loadRoles: ReturnType<typeof vi.fn>;
    currentCompanyUsers: ReturnType<typeof vi.fn>;
    availableRoles: ReturnType<typeof vi.fn>;
    pagination: ReturnType<typeof vi.fn>;
    isLoading: ReturnType<typeof vi.fn>;
    isListEmpty: ReturnType<typeof vi.fn>;
  };
  let loadListCalls: { filters: ListFilters; page?: number }[];

  function render(roles: string[] | null): void {
    companyUsersStoreMock = {
      loadList: vi.fn().mockImplementation((filters: ListFilters) => {
        loadListCalls.push({ filters });
        return Promise.resolve();
      }),
      loadRoles: vi.fn().mockResolvedValue(undefined),
      currentCompanyUsers: vi.fn().mockReturnValue(null),
      availableRoles: vi.fn().mockReturnValue(SAMPLE_ROLES),
      pagination: vi.fn().mockReturnValue({ page: 1, size: 10, total: 0 }),
      isLoading: vi.fn().mockReturnValue(false),
      isListEmpty: vi.fn().mockReturnValue(true),
    };

    TestBed.configureTestingModule({
      imports: [TeamListComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CompanyUsersStore, useValue: companyUsersStoreMock },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    if (roles !== null) {
      authStore.setUser(userWithRoles(roles));
    }
    fixture = TestBed.createComponent(TeamListComponent);
    component = fixture.componentInstance;
    loadListCalls = [];
  }

  function flushInitialLoad(): void {
    // The component calls store.loadList on init via an effect
    // tied to the filters signal. We trigger change detection
    // and then assert on the captured calls.
    fixture.detectChanges();
  }

  // -------- warm-up (vitest orphan offset) --------

  it('warm-up — component instantiates', () => {
    render(['COMPANY_ADMIN']);
    expect(component).toBeTruthy();
  });

  // -------- header + visibility --------

  it('renders the H1 "Equipo"', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Equipo');
  });

  it('renders the "+ Nuevo usuario" button when the user is COMPANY_ADMIN', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/team/new"]',
    );
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Nuevo usuario');
  });

  it('hides the "+ Nuevo usuario" button when the user is COMPANY_OPERATOR', () => {
    render(['COMPANY_OPERATOR']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/team/new"]',
    );
    expect(button).toBeNull();
  });

  // -------- initial load --------

  it('triggers store.loadList on init with empty filters', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();

    expect(loadListCalls.length).toBeGreaterThan(0);
    expect(loadListCalls[0].filters).toBeDefined();
  });

  it('triggers store.loadRoles on init to populate the role filter', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();

    expect(companyUsersStoreMock.loadRoles).toHaveBeenCalledTimes(1);
  });

  // -------- empty state --------

  it('renders the empty state when the list is empty and not loading', () => {
    render(['COMPANY_ADMIN']);
    companyUsersStoreMock.currentCompanyUsers.mockReturnValue([]);
    companyUsersStoreMock.isListEmpty.mockReturnValue(true);
    flushInitialLoad();
    fixture.detectChanges();

    const emptyState = (fixture.nativeElement as HTMLElement).querySelector(
      'app-empty-state',
    );
    expect(emptyState).toBeTruthy();
    const text = emptyState?.textContent ?? '';
    expect(text).toContain('Todavía no tenés más usuarios en tu equipo');
  });

  // -------- populated state --------

  it('renders the data table when the list has rows', () => {
    render(['COMPANY_ADMIN']);
    const users = [makeSummary('u1', 'juan'), makeSummary('u2', 'maria', 'DISABLED')];
    companyUsersStoreMock.currentCompanyUsers.mockReturnValue(users);
    companyUsersStoreMock.isListEmpty.mockReturnValue(false);
    companyUsersStoreMock.pagination.mockReturnValue({ page: 1, size: 10, total: 2 });
    flushInitialLoad();
    fixture.detectChanges();

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tr[data-row]');
    expect(rows.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('juan');
    expect(text).toContain('maria');
    // Roles column renders role chips via app-role-chip
    // (Status column renders app-status-badge)
    const statusBadges = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'app-status-badge',
    );
    expect(statusBadges.length).toBe(2);
  });

  // -------- pagination --------

  it('renders the pagination summary with total count', () => {
    render(['COMPANY_ADMIN']);
    const users = [makeSummary('u1', 'juan')];
    companyUsersStoreMock.currentCompanyUsers.mockReturnValue(users);
    companyUsersStoreMock.isListEmpty.mockReturnValue(false);
    companyUsersStoreMock.pagination.mockReturnValue({ page: 1, size: 10, total: 25 });
    flushInitialLoad();
    fixture.detectChanges();

    const summary = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-pagination-summary]',
    );
    expect(summary?.textContent).toContain('25 resultados');
  });

  it('disables "Anterior" on page 1', () => {
    render(['COMPANY_ADMIN']);
    const users = [makeSummary('u1', 'juan')];
    companyUsersStoreMock.currentCompanyUsers.mockReturnValue(users);
    companyUsersStoreMock.isListEmpty.mockReturnValue(false);
    companyUsersStoreMock.pagination.mockReturnValue({ page: 1, size: 10, total: 25 });
    flushInitialLoad();
    fixture.detectChanges();

    const prev = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-page-prev]',
    ) as HTMLButtonElement | null;
    expect(prev?.disabled).toBe(true);
  });
});
