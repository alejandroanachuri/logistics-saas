import { TestBed, ComponentFixture } from '@angular/core/testing';
import {
  provideRouter,
  ActivatedRoute,
  convertToParamMap,
} from '@angular/router';
import { vi } from 'vitest';

import { ShipmentDetailComponent } from './shipment-detail';
import { AuthStore } from '../../../core/state/auth-store';
import { ShipmentsStore } from '../../../core/state/shipments-store';
import {
  AuthUser,
  ShipmentDetail,
  ShipmentPackage,
  TrackingEvent,
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

function makePackage(
  id: string,
  overrides: Partial<ShipmentPackage> = {},
): ShipmentPackage {
  return {
    id,
    shipmentId: 's-1',
    qrCode: `LGST-${id.toUpperCase()}ABC123`,
    previousStatus: null,
    status: 'PRE_ALTA',
    weightKg: 1.5,
    volumeCm3: null,
    dimensionsCm: '30x20x15',
    contentDescription: 'Sample contents',
    declaredValue: 1000,
    declaredCurrency: 'ARS',
    hasInsurance: false,
    isFragile: false,
    isUrgent: false,
    requiresSignature: false,
    requiresIdCheck: false,
    category: 'GENERAL',
    receptionCondition: 'BUENO',
    receptionNotes: null,
    ...overrides,
  };
}

function makeDetail(
  overrides: Partial<ShipmentDetail> = {},
): ShipmentDetail {
  return {
    id: 's-1',
    trackingId: 'LGST-ABC12345',
    code: 'PO-12345',
    status: 'PRE_ALTA',
    sender: { id: 'c-1', name: 'Juan Pérez' },
    receiver: { id: 'c-2', name: 'María García' },
    deliveryAddress: {
      id: 'a-1',
      displayLabel: 'Av. Corrientes 1234, CABA',
    },
    originBranch: { id: 'b-1', code: 'BUE-CENTRO', name: 'Buenos Aires Centro' },
    destinationBranch: { id: 'b-2', code: 'CBA-CENTRO', name: 'Córdoba Centro' },
    serviceLevel: { id: 'sl-1', code: 'EXPRESS', name: 'Express 24h' },
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: 'Dejar en portería',
    packages: [makePackage('p-1')],
    totalWeightKg: 1.5,
    totalCost: 1500,
    createdAt: '2026-01-15T10:30:00Z',
    updatedAt: null,
    latestEvent: null,
    ...overrides,
  };
}

function makeEvent(
  id: string,
  overrides: Partial<TrackingEvent> = {},
): TrackingEvent {
  return {
    id,
    packageId: 'p-1',
    eventType: 'package_created',
    eventTimestamp: '2026-01-15T10:00:00Z',
    branchId: null,
    userId: 'u-1',
    eventSource: 'OPERADOR_SUCURSAL',
    metadata: null,
    createdAt: '2026-01-15T10:00:00Z',
    ...overrides,
  };
}

describe('ShipmentDetailComponent', () => {
  let fixture: ComponentFixture<ShipmentDetailComponent>;
  let component: ShipmentDetailComponent;
  let shipmentsStoreMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    loadTimeline: ReturnType<typeof vi.fn>;
    validate: ReturnType<typeof vi.fn>;
    reject: ReturnType<typeof vi.fn>;
    cancel: ReturnType<typeof vi.fn>;
    clearDetail: ReturnType<typeof vi.fn>;
    currentTimeline: ReturnType<typeof vi.fn>;
  };
  let activatedRouteId = 's-1';

  function render(
    currentUser: AuthUser | null,
    detail: ShipmentDetail,
    timeline: TrackingEvent[] = [],
  ): void {
    shipmentsStoreMock = {
      loadDetail: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
        return detail;
      }),
      loadTimeline: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
      }),
      validate: vi.fn().mockImplementation(async () => detail),
      reject: vi.fn().mockImplementation(async () => detail),
      cancel: vi.fn().mockImplementation(async () => detail),
      clearDetail: vi.fn(),
      currentTimeline: vi.fn().mockReturnValue(timeline),
    };

    TestBed.configureTestingModule({
      imports: [ShipmentDetailComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: ShipmentsStore, useValue: shipmentsStoreMock },
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
    fixture = TestBed.createComponent(ShipmentDetailComponent);
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

  // -------- header --------

  it('renders the trackingId', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const trackingEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-tracking-id]',
    );
    expect(trackingEl?.textContent).toContain('LGST-ABC12345');
  });

  it('renders the shipment-status-badge with the current status', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail({ status: 'CREADO' }));
    await Promise.resolve();
    fixture.detectChanges();

    const badge = (fixture.nativeElement as HTMLElement).querySelector(
      'app-shipment-status-badge [data-status]',
    );
    expect(badge).toBeTruthy();
    expect(badge?.getAttribute('data-status')).toBe('CREADO');
  });

  // -------- tabs --------

  it('starts on the info tab and renders the info section', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const infoSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-info"]',
    );
    expect(infoSection).toBeTruthy();
  });

  it('switches to the packages tab when clicked', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const packagesTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-packages-button"]',
    ) as HTMLButtonElement | null;
    packagesTab?.click();
    fixture.detectChanges();

    const packagesSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-packages"]',
    );
    expect(packagesSection).toBeTruthy();
  });

  it('switches to the timeline tab when clicked', async () => {
    render(makeUser(['COMPANY_ADMIN'], 'me'), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const timelineTab = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-timeline-button"]',
    ) as HTMLButtonElement | null;
    timelineTab?.click();
    fixture.detectChanges();

    const timelineSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-timeline"]',
    );
    expect(timelineSection).toBeTruthy();
  });

  // -------- action button visibility --------

  it('shows "Validar" button when status is PRE_ALTA and user is admin', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'PRE_ALTA' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const validateBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="validate-shipment-button"]',
    ) as HTMLButtonElement | null;
    expect(validateBtn).toBeTruthy();
    expect(validateBtn?.disabled).toBe(false);
  });

  it('shows "Validar" button when status is PRE_ALTA and user is operator', async () => {
    render(
      makeUser(['COMPANY_OPERATOR'], 'me'),
      makeDetail({ status: 'PRE_ALTA' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const validateBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="validate-shipment-button"]',
    ) as HTMLButtonElement | null;
    expect(validateBtn).toBeTruthy();
    expect(validateBtn?.disabled).toBe(false);
  });

  it('hides "Validar" button when status is not PRE_ALTA', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'CREADO' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const validateBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="validate-shipment-button"]',
    );
    expect(validateBtn).toBeNull();
  });

  it('calls store.validate when "Validar" is clicked', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'PRE_ALTA' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    component.selectedTab.set('info');
    fixture.detectChanges();
    component.showValidateConfirm.set(true);
    fixture.detectChanges();

    await component.validate();
    await Promise.resolve();

    expect(shipmentsStoreMock.validate).toHaveBeenCalledWith('s-1');
  });

  it('shows "Rechazar" button only when status is PRE_ALTA', async () => {
    // PRE_ALTA -> button visible.
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'PRE_ALTA' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const rejectBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="reject-shipment-button"]',
    ) as HTMLButtonElement | null;
    expect(rejectBtn).toBeTruthy();

    // Switch to non-PRE_ALTA by mutating the cached detail
    // through the store mock — the component reads via signal
    // so flipping the mock return flips the rendered view.
    const detailCREADO = makeDetail({ status: 'CREADO' });
    shipmentsStoreMock.loadDetail.mockResolvedValueOnce(detailCREADO);
    TestBed.resetTestingModule();
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      detailCREADO,
    );
    await Promise.resolve();
    fixture.detectChanges();

    const rejectBtnAfter = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="reject-shipment-button"]',
    );
    expect(rejectBtnAfter).toBeNull();
  });

  it('shows "Cancelar" button only for COMPANY_ADMIN and non-final status', async () => {
    // Admin + non-final: button visible.
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'CREADO' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const cancelBtn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="cancel-shipment-button"]',
    ) as HTMLButtonElement | null;
    expect(cancelBtn).toBeTruthy();

    // Operator + non-final: button NOT visible (admin only).
    TestBed.resetTestingModule();
    render(
      makeUser(['COMPANY_OPERATOR'], 'me'),
      makeDetail({ status: 'CREADO' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const cancelBtnAsOp = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="cancel-shipment-button"]',
    );
    expect(cancelBtnAsOp).toBeNull();

    // Admin + ENTREGADO: button NOT visible (final state).
    TestBed.resetTestingModule();
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'ENTREGADO' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const cancelBtnFinal = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="cancel-shipment-button"]',
    );
    expect(cancelBtnFinal).toBeNull();
  });

  it('shows "Editar" button only when status is PRE_ALTA', async () => {
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'PRE_ALTA' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const editBtn = (fixture.nativeElement as HTMLElement).querySelector(
      'a[data-testid="edit-shipment-button"]',
    );
    expect(editBtn).toBeTruthy();

    TestBed.resetTestingModule();
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail({ status: 'CREADO' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const editBtnAfter = (fixture.nativeElement as HTMLElement).querySelector(
      'a[data-testid="edit-shipment-button"]',
    );
    expect(editBtnAfter).toBeNull();
  });

  // -------- timeline rendering --------

  it('renders timeline events when the timeline tab is open', async () => {
    const events = [
      makeEvent('e-1', { eventType: 'package_created' }),
      makeEvent('e-2', { eventType: 'in_transit_to_hub' }),
    ];
    render(
      makeUser(['COMPANY_ADMIN'], 'me'),
      makeDetail(),
      events,
    );
    await Promise.resolve();
    fixture.detectChanges();

    component.selectedTab.set('timeline');
    fixture.detectChanges();

    const timelineSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-timeline"]',
    );
    expect(timelineSection).toBeTruthy();
    const eventEls = timelineSection!.querySelectorAll('[data-event]');
    expect(eventEls.length).toBe(2);
    expect(timelineSection?.textContent).toContain('Paquete creado');
    expect(timelineSection?.textContent).toContain('En tránsito a hub');
  });

  // -------- packages rendering --------

  it('renders package-status-badge per package in the packages tab', async () => {
    const detail = makeDetail({
      packages: [
        makePackage('p-1', { status: 'PRE_ALTA' }),
        makePackage('p-2', { status: 'EN_TRANSITO_A_HUB' }),
      ],
    });
    render(makeUser(['COMPANY_ADMIN'], 'me'), detail);
    await Promise.resolve();
    fixture.detectChanges();

    component.selectedTab.set('packages');
    fixture.detectChanges();

    const packagesSection = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="tab-packages"]',
    );
    expect(packagesSection).toBeTruthy();
    const badges = packagesSection!.querySelectorAll(
      'app-package-status-badge [data-status]',
    );
    expect(badges.length).toBe(2);
    expect(badges[0].getAttribute('data-status')).toBe('PRE_ALTA');
    expect(badges[1].getAttribute('data-status')).toBe('EN_TRANSITO_A_HUB');
  });
});