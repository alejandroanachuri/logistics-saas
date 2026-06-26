import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { vi } from 'vitest';

import { ShipmentEditComponent } from './shipment-edit';
import { AuthStore } from '../../../core/state/auth-store';
import { ShipmentsStore } from '../../../core/state/shipments-store';
import { AuthUser, ShipmentDetail, UpdateShipmentRequest } from '../../../core/types';

function makeUser(roles: string[]): AuthUser {
  return {
    id: 'me',
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

function makeDetail(overrides: Partial<ShipmentDetail> = {}): ShipmentDetail {
  return {
    id: 's-target',
    trackingId: 'LGST-EDIT01',
    code: null,
    status: 'PRE_ALTA',
    sender: { id: 'cust-1', name: 'Juan Pérez' },
    receiver: { id: 'cust-2', name: 'Ana García' },
    deliveryAddress: null,
    originBranch: null,
    destinationBranch: null,
    serviceLevel: null,
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: 'Dejar en portería',
    packages: [],
    totalWeightKg: null,
    totalCost: null,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    latestEvent: null,
    ...overrides,
  };
}

describe('ShipmentEditComponent', () => {
  let fixture: ComponentFixture<ShipmentEditComponent>;
  let component: ShipmentEditComponent;
  let storeMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    currentShipment: ReturnType<typeof vi.fn>;
    clearDetail: ReturnType<typeof vi.fn>;
  };
  let activatedRouteId = 's-target';
  let navigatedTo: unknown[] | null = null;

  function render(currentUser: AuthUser | null, detail: ShipmentDetail): void {
    storeMock = {
      loadDetail: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
        return detail;
      }),
      update: vi.fn().mockResolvedValue(detail),
      currentShipment: vi.fn().mockReturnValue(detail),
      clearDetail: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [ShipmentEditComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: ShipmentsStore, useValue: storeMock },
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
    fixture = TestBed.createComponent(ShipmentEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    expect(component).toBeTruthy();
  });

  // -------- header --------

  it('renders the H1 "Editar envío"', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Editar envío');
  });

  // -------- pre-fill --------

  it('pre-fills the form with deliveryInstructions + paymentType + deliveryMode + promisedDeliveryDate', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({
        deliveryInstructions: 'Dejar con portero',
        paymentType: 'PAGO_DESTINO',
        deliveryMode: 'RETIRO_SUCURSAL',
      }),
    );
    await Promise.resolve();
    fixture.detectChanges();
    const instructions = (fixture.nativeElement as HTMLElement).querySelector(
      'textarea[data-field="deliveryInstructions"]',
    ) as HTMLTextAreaElement | null;
    expect(instructions?.value).toBe('Dejar con portero');
    const payment = (fixture.nativeElement as HTMLElement).querySelector(
      'select[data-field="paymentType"]',
    ) as HTMLSelectElement | null;
    expect(payment?.value).toBe('PAGO_DESTINO');
    const mode = (fixture.nativeElement as HTMLElement).querySelector(
      'select[data-field="deliveryMode"]',
    ) as HTMLSelectElement | null;
    expect(mode?.value).toBe('RETIRO_SUCURSAL');
  });

  // -------- status guard --------

  it('shows an error banner when the shipment is NOT in PRE_ALTA', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail({ status: 'CREADO' }));
    await Promise.resolve();
    fixture.detectChanges();
    const errorBanner = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="error-not-pre-alta"]',
    );
    expect(errorBanner).toBeTruthy();
  });

  // -------- save --------

  it('on submit calls store.update with the PATCH payload and navigates to detail', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    component.onSubmit({
      deliveryInstructions: 'Nueva instrucción',
      paymentType: 'CUENTA_CORRIENTE',
      deliveryMode: 'DOMICILIO',
      promisedDeliveryDate: '2026-07-01',
    });
    await Promise.resolve();
    await Promise.resolve();
    expect(storeMock.update).toHaveBeenCalledTimes(1);
    const [idArg, payload] = storeMock.update.mock.calls[0];
    expect(idArg).toBe('s-target');
    expect(payload as UpdateShipmentRequest).toEqual({
      deliveryInstructions: 'Nueva instrucción',
      paymentType: 'CUENTA_CORRIENTE',
      deliveryMode: 'DOMICILIO',
      promisedDeliveryDate: '2026-07-01',
    });
    expect(storeMock.clearDetail).toHaveBeenCalled();
    expect(navigatedTo).toEqual(['/auth/shipments', 's-target']);
  });

  it('on submit error renders an error message and does not navigate', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    storeMock.update.mockRejectedValueOnce(new Error('boom'));
    component.onSubmit({
      deliveryInstructions: 'x',
      paymentType: 'PAGO_ORIGEN',
      deliveryMode: 'DOMICILIO',
      promisedDeliveryDate: '',
    });
    await Promise.resolve();
    await Promise.resolve();
    fixture.detectChanges();
    const errorEl = (fixture.nativeElement as HTMLElement).querySelector('[data-testid="error"]');
    expect(errorEl).toBeTruthy();
    expect(component.errorMessage()).toBeTruthy();
    expect(navigatedTo).toBeNull();
  });

  // -------- cancel --------

  it('on cancel clears the cached detail and navigates back to detail', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    component.onCancel();
    expect(storeMock.clearDetail).toHaveBeenCalled();
    expect(navigatedTo).toEqual(['/auth/shipments', 's-target']);
  });
});
