import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { ShipmentListComponent } from './shipment-list';
import { AuthStore } from '../../../core/state/auth-store';
import {
  ShipmentsStore,
  ShipmentPaginationState,
} from '../../../core/state/shipments-store';
import { ShipmentListFilters } from '../../../core/types';
import { AuthUser, PageResponse, ShipmentSummary } from '../../../core/types';

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
  overrides: Partial<ShipmentSummary> = {},
): ShipmentSummary {
  return {
    id,
    trackingId: `LGST-${id.toUpperCase()}ABC123`,
    code: null,
    status: 'PRE_ALTA',
    senderName: 'Juan Pérez',
    receiverName: 'María García',
    totalWeightKg: 1.5,
    createdAt: '2026-01-15T10:30:00Z',
    ...overrides,
  };
}

function makePage(
  rows: ShipmentSummary[],
  page = 1,
  total = rows.length,
): PageResponse<ShipmentSummary> {
  return { data: rows, total, page, size: 20 };
}

describe('ShipmentListComponent', () => {
  let fixture: ComponentFixture<ShipmentListComponent>;
  let component: ShipmentListComponent;
  let shipmentsStoreMock: {
    loadList: ReturnType<typeof vi.fn>;
    currentShipments: ReturnType<typeof vi.fn>;
    pagination: ReturnType<typeof vi.fn>;
    isLoading: ReturnType<typeof vi.fn>;
    isListEmpty: ReturnType<typeof vi.fn>;
  };
  let loadListCalls: { filters: ShipmentListFilters & { page?: number; size?: number; sort?: string } }[];
  let routerNavigateSpy: ReturnType<typeof vi.spyOn>;

  function render(roles: string[] | null): void {
    shipmentsStoreMock = {
      loadList: vi.fn().mockImplementation((filters: ShipmentListFilters) => {
        loadListCalls.push({ filters });
        return Promise.resolve();
      }),
      currentShipments: vi.fn().mockReturnValue(null),
      pagination: vi
        .fn()
        .mockReturnValue({ page: 1, size: 20, total: 0 } as ShipmentPaginationState),
      isLoading: vi.fn().mockReturnValue(false),
      isListEmpty: vi.fn().mockReturnValue(true),
    };

    TestBed.configureTestingModule({
      imports: [ShipmentListComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: ShipmentsStore, useValue: shipmentsStoreMock },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    if (roles !== null) authStore.setUser(makeUser(roles));
    fixture = TestBed.createComponent(ShipmentListComponent);
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

  it('renders the H1 "Envíos"', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Envíos');
  });

  it('renders the "+ Nuevo envío" button when the user is COMPANY_ADMIN', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/shipments/new"]',
    );
    expect(button).toBeTruthy();
    expect(button?.textContent).toContain('Nuevo envío');
  });

  it('renders the "+ Nuevo envío" button when the user is COMPANY_OPERATOR', () => {
    render(['COMPANY_OPERATOR']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/shipments/new"]',
    );
    expect(button).toBeTruthy();
  });

  it('hides the "+ Nuevo envío" button when the user is COMPANY_VIEWER', () => {
    render(['COMPANY_VIEWER']);
    flushInitialLoad();
    fixture.detectChanges();

    const button = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/shipments/new"]',
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
    shipmentsStoreMock.currentShipments.mockReturnValue([]);
    shipmentsStoreMock.isListEmpty.mockReturnValue(true);
    flushInitialLoad();
    fixture.detectChanges();

    const emptyState = (fixture.nativeElement as HTMLElement).querySelector(
      'app-empty-state',
    );
    expect(emptyState).toBeTruthy();
    const text = emptyState?.textContent ?? '';
    expect(text).toContain('envíos');
  });

  // -------- populated state --------

  it('lists shipments from store', () => {
    render(['COMPANY_ADMIN']);
    const rows = [
      makeSummary('s1'),
      makeSummary('s2', {
        trackingId: 'LGST-ZZZZZZZZ',
        status: 'EN_TRANSITO_A_HUB',
        senderName: 'Empresa A',
        receiverName: 'Empresa B',
      }),
    ];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
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
    expect(text).toContain('LGST-S1ABC123');
    expect(text).toContain('LGST-ZZZZZZZZ');
    expect(text).toContain('Juan Pérez');
    expect(text).toContain('María García');
    expect(text).toContain('Empresa A');
    expect(text).toContain('Empresa B');
  });

  it('renders trackingId in monospace', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('s1')];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 1,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const monoEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-tracking-id]',
    ) as HTMLElement | null;
    expect(monoEl).toBeTruthy();
    expect(monoEl?.className).toContain('font-mono');
    expect(monoEl?.textContent).toContain('LGST-S1ABC123');
  });

  it('renders one shipment-status-badge per row', () => {
    render(['COMPANY_ADMIN']);
    const rows = [
      makeSummary('s1', { status: 'PRE_ALTA' }),
      makeSummary('s2', { status: 'EN_TRANSITO_A_HUB' }),
    ];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 2,
    });
    flushInitialLoad();
    fixture.detectChanges();

    const badges = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'app-shipment-status-badge [data-status]',
    );
    expect(badges.length).toBe(2);
    expect(badges[0].getAttribute('data-status')).toBe('PRE_ALTA');
    expect(badges[1].getAttribute('data-status')).toBe('EN_TRANSITO_A_HUB');
  });

  // -------- navigation --------

  it('exposes a link to detail per row', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('s-abc'), makeSummary('s-def')];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
      page: 1,
      size: 20,
      total: 2,
    });
    flushInitialLoad();
    fixture.detectChanges();

    // RouterLink rewrites the href at runtime, so the rendered
    // anchor must point at the detail route for each row.
    const detailLinks = (fixture.nativeElement as HTMLElement).querySelectorAll(
      'a[href="/auth/shipments/s-abc"], a[href="/auth/shipments/s-def"]',
    );
    expect(detailLinks.length).toBeGreaterThanOrEqual(2);
  });

  it('goToDetail navigates to /auth/shipments/:id', () => {
    render(['COMPANY_ADMIN']);
    flushInitialLoad();
    fixture.detectChanges();

    component.goToDetail('s-xyz');
    expect(routerNavigateSpy).toHaveBeenCalledWith(['/auth/shipments', 's-xyz']);
  });

  // -------- pagination --------

  it('renders the pagination summary with total count', () => {
    render(['COMPANY_ADMIN']);
    const rows = [makeSummary('s1')];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
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
    const rows = [makeSummary('s1')];
    shipmentsStoreMock.currentShipments.mockReturnValue(rows);
    shipmentsStoreMock.isListEmpty.mockReturnValue(false);
    shipmentsStoreMock.pagination.mockReturnValue({
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