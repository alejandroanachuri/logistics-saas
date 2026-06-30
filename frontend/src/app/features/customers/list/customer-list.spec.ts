import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { CustomerListComponent } from './customer-list';
import { AuthStore } from '../../../core/state/auth-store';
import {
  CustomersStore,
  CustomerPaginationState,
} from '../../../core/state/customers-store';
import { CustomerListFilters } from '../../../core/types';
import {
  AuthUser,
  CustomerSummary,
  PageResponse,
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

function makeSummary(
  id: string,
  overrides: Partial<CustomerSummary> = {},
): CustomerSummary {
  return {
    id,
    firstName: 'Juan',
    lastName: 'Pérez',
    razonSocial: null,
    email: 'juan@test.com',
    phone: '+541112345678',
    dni: '12345678',
    cuitCuil: null,
    taxCondition: 'CONSUMIDOR_FINAL',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makePage(
  rows: CustomerSummary[],
  page = 1,
  total = rows.length,
): PageResponse<CustomerSummary> {
  return { data: rows, total, page, size: 20 };
}

describe('CustomerListComponent', () => {
  let fixture: ComponentFixture<CustomerListComponent>;
  let component: CustomerListComponent;
  let customersStoreMock: {
    loadList: ReturnType<typeof vi.fn>;
    currentCustomers: ReturnType<typeof vi.fn>;
    pagination: ReturnType<typeof vi.fn>;
    isLoading: ReturnType<typeof vi.fn>;
    isListEmpty: ReturnType<typeof vi.fn>;
  };
  let loadListCalls: { filters: CustomerListFilters & { page?: number; size?: number; sort?: string } }[];
  let routerNavigateSpy: ReturnType<typeof vi.spyOn>;

  function render(roles: string[] | null): void {
    customersStoreMock = {
      loadList: vi.fn().mockImplementation((filters: CustomerListFilters) => {
        loadListCalls.push({ filters });
        return Promise.resolve();
      }),
      currentCustomers: vi.fn().mockReturnValue(null),
      pagination: vi
        .fn()
        .mockReturnValue({ page: 1, size: 20, total: 0 } as CustomerPaginationState),
      isLoading: vi.fn().mockReturnValue(false),
      isListEmpty: vi.fn().mockReturnValue(true),
    };

    TestBed.configureTestingModule({
      imports: [CustomerListComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CustomersStore, useValue: customersStoreMock },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    if (roles !== null) authStore.setUser(makeUser(roles));
    fixture = TestBed.createComponent(CustomerListComponent);
    component = fixture.componentInstance;
    routerNavigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate');
    loadListCalls = [];
  }

  function flushInitialLoad(): void {
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(['COMPANY_ADMIN']);
    expect(component).toBeTruthy();
  });

  // -------- header + visibility --------

  it('renders the H1 "Clientes"', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Clientes');
  });

  it('renders the "+ Nuevo cliente" button when the user is COMPANY_ADMIN', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers/new"]',
    );
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Nuevo cliente');
  });

  it('renders the "+ Nuevo cliente" button when the user is COMPANY_OPERATOR', () => {
    render(['COMPANY_OPERATOR']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers/new"]',
    );
    expect(button).toBeTruthy();
  });

  it('hides the "+ Nuevo cliente" button when the user is COMPANY_VIEWER', () => {
    render(['COMPANY_VIEWER']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers/new"]',
    );
    expect(button).toBeNull();
  });

  // -------- initial load --------

  it('triggers store.loadList on init with empty filters', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();

    expect(loadListCalls.length).toBeGreaterThan(0);
    expect(loadListCalls[0].filters).toBeDefined();
    expect(loadListCalls[0].filters.page).toBe(1);
  });

  // -------- empty state --------

  it('renders the empty state when the list is empty and not loading', () => {
    render(['COMPANY_ADMIN']);
    customersStoreMock.currentCustomers.mockReturnValue([]);
    customersStoreMock.isListEmpty.mockReturnValue(true);
    flushInitialLoad();
    fixture.detectChanges();

    const emptyState = (fixture.nativeElement as HTMLElement).querySelector(
      'app-empty-state',
    );
    expect(emptyState).toBeTruthy();
    const text = emptyState?.textContent ?? '';
    expect(text).toContain('Todavía no tenés clientes');
  });

  it('navigates to /auth/customers/new when the empty-state CTA is clicked', () => {
    render(['COMPANY_ADMIN']);
    customersStoreMock.currentCustomers.mockReturnValue([]);
    customersStoreMock.isListEmpty.mockReturnValue(true);
    flushInitialLoad();
    fixture.detectChanges();

    const emptyState = (fixture.nativeElement as HTMLElement).querySelector(
      'app-empty-state',
    ) as HTMLElement;
    const ctaButton = emptyState.querySelector('button') as HTMLButtonElement;
    ctaButton.click();

    expect(routerNavigateSpy).toHaveBeenCalledWith(['/auth/customers/new']);
  });

  // -------- populated state --------

  it('renders the data table when the list has rows', () => {
    render(['COMPANY_ADMIN']);
    const rows = [
      makeSummary('c1'),
      makeSummary('c2', { firstName: 'María', lastName: 'García', status: 'DISABLED' }),
    ];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 2,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const tableRows = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'tr[data-row]',
    );
    expect(tableRows.length).toBe(2);
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Juan');
    expect(text).toContain('María');
    const statusBadges = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'app-status-badge',
    );
    expect(statusBadges.length).toBe(2);
  });

  // -------- field-level security --------

  it('masks DNI for COMPANY_VIEWER role', () => {
    render(['COMPANY_VIEWER']);
    const rows = [makeSummary('c1', { dni: '12345678', phone: '+5491100000000' })];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 1,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('12***78');
    expect(text).not.toContain('12345678');
  });

  it('masks DNI for COMPANY_DRIVER role', () => {
    render(['COMPANY_DRIVER']);
    const rows = [makeSummary('c1', { dni: '12345678', phone: '+5491100000000' })];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 1,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('12***78');
    expect(text).not.toContain('12345678');
  });

  it('shows full DNI for COMPANY_ADMIN role', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('c1', { dni: '12345678', phone: '+5491100000000' })];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 1,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('12345678');
  });

  // -------- pagination --------

  it('renders the pagination summary with total count', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('c1')];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 42,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const summary = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-pagination-summary]',
    );
    expect(summary?.textContent).toContain('42 resultados');
  });

  it('disables "Anterior" on page 1', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('c1')];
    customersStoreMock.currentCustomers.mockReturnValue(rows);
    customersStoreMock.isListEmpty.mockReturnValue(false);
    customersStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 42,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const prev = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-page-prev]',
    ) as HTMLButtonElement | null;
    expect(prev?.disabled).toBe(true);
  });
});
