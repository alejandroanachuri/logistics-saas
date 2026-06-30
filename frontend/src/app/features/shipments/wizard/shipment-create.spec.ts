import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';
import { firstValueFrom, of, throwError } from 'rxjs';

import { ShipmentCreateComponent } from './shipment-create';
import { ShipmentsStore } from '../../../core/state/shipments-store';
import {
  CreateShipmentResponse,
  CustomerSummary,
  Shipment,
  ShipmentPackage,
} from '../../../core/types';

function makeCustomer(overrides: Partial<CustomerSummary> = {}): CustomerSummary {
  return {
    id: 'cust-1',
    firstName: 'Juan',
    lastName: 'Pérez',
    razonSocial: null,
    email: 'juan@test.com',
    phone: '+5491100000000',
    dni: '12345678',
    cuitCuil: null,
    taxCondition: 'CONSUMIDOR_FINAL',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

function makePackage(overrides: Partial<ShipmentPackage> = {}): ShipmentPackage {
  return {
    id: 'pkg-1',
    shipmentId: 's-new',
    qrCode: 'LGST-AAA',
    previousStatus: null,
    status: 'PRE_ALTA',
    weightKg: 2.0,
    volumeCm3: null,
    dimensionsCm: null,
    contentDescription: 'Caja con libros',
    declaredValue: null,
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

function makeResponse(): CreateShipmentResponse {
  const shipment: Shipment = {
    id: 's-new',
    trackingId: 'LGST-NEW123',
    code: null,
    shipmentType: 'NORMAL',
    senderId: 'cust-1',
    receiverId: 'cust-2',
    deliveryAddressId: 'addr-1',
    originBranchId: 'b-1',
    destinationBranchId: 'b-2',
    serviceLevelId: 'sl-1',
    paymentType: 'PAGO_ORIGEN',
    deliveryMode: 'DOMICILIO',
    deliveryInstructions: null,
    status: 'PRE_ALTA',
    promisedDeliveryDate: null,
    slaStatus: 'EN_PLAZO',
    totalWeightKg: 2.0,
    totalCost: null,
    createdBy: 'me',
    createdAt: '2026-06-26T00:00:00Z',
    updatedAt: null,
  };
  return { shipment, packages: [makePackage()] };
}

describe('ShipmentCreateComponent', () => {
  let fixture: ComponentFixture<ShipmentCreateComponent>;
  let component: ShipmentCreateComponent;
  let storeMock: {
    loadCatalogs: ReturnType<typeof vi.fn>;
    wizardPatchData: ReturnType<typeof vi.fn>;
    wizardReset: ReturnType<typeof vi.fn>;
    wizardSubmit: ReturnType<typeof vi.fn>;
    wizardGoToStep: ReturnType<typeof vi.fn>;
  };
  let navigatedTo: unknown[] | null = null;

  function render(): void {
    storeMock = {
      loadCatalogs: vi.fn().mockResolvedValue(undefined),
      wizardPatchData: vi.fn(),
      wizardReset: vi.fn(),
      wizardSubmit: vi.fn(),
      wizardGoToStep: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [ShipmentCreateComponent],
      providers: [provideRouter([]), { provide: ShipmentsStore, useValue: storeMock }],
    });

    const router = TestBed.inject(Router);
    navigatedTo = null;
    vi.spyOn(router, 'navigate').mockImplementation(async (commands: readonly unknown[]) => {
      navigatedTo = [...commands];
      return true;
    });

    fixture = TestBed.createComponent(ShipmentCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render();
    expect(component).toBeTruthy();
  });

  // -------- catalog loading --------

  it('loads catalogs on init', async () => {
    render();
    await Promise.resolve();
    expect(storeMock.loadCatalogs).toHaveBeenCalled();
  });

  // -------- stepper header --------

  it('renders the 3 step labels', () => {
    render();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="step-label-customers"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-label-packages"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-label-confirm"]')).toBeTruthy();
  });

  it('starts on step 0 (customers)', () => {
    render();
    expect(component.currentStepIndex()).toBe(0);
  });

  // -------- step navigation --------

  it('next() advances currentStepIndex when step 0 has valid customer selections', async () => {
    render();
    await fixture.whenStable();
    fixture.detectChanges();
    expect(component.customersStep).toBeDefined();
    const child = component.customersStep!;
    child.selectCustomer(makeCustomer({ id: 'cust-1' }), 'sender');
    child.selectCustomer(makeCustomer({ id: 'cust-2' }), 'receiver');
    fixture.detectChanges();
    // Use the SAME child reference inside the assertion.
    const senderS = child.senderSelection();
    const receiverS = child.receiverSelection();
    if (senderS && receiverS && senderS.id !== receiverS.id) {
      expect(child.canAdvance()).toBe(true);
    } else {
      throw new Error(`Pre-condition failed: sender=${senderS?.id} receiver=${receiverS?.id}`);
    }
    // Read the orchestrator's own canAdvance.
    const orchestratorCanAdvance = component.canAdvance();
    expect(orchestratorCanAdvance).toBe(true);
    component.next();
    expect(component.currentStepIndex()).toBe(1);
  });

  it('next() does NOT advance when step 0 has no selections', () => {
    render();
    component.next();
    expect(component.currentStepIndex()).toBe(0);
  });

  it('next() caps at 2 (no overshoot)', () => {
    render();
    component.currentStepIndex.set(2);
    component.next();
    expect(component.currentStepIndex()).toBe(2);
  });

  it('previous() walks back and caps at 0', () => {
    render();
    component.currentStepIndex.set(2);
    component.previous();
    expect(component.currentStepIndex()).toBe(1);
    component.previous();
    expect(component.currentStepIndex()).toBe(0);
    component.previous();
    expect(component.currentStepIndex()).toBe(0);
  });

  // -------- step bodies in the DOM --------

  it('keeps the 3 step bodies in the DOM and toggles via [class.hidden]', () => {
    render();
    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('[data-testid="step-body-customers"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-body-packages"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="step-body-confirm"]')).toBeTruthy();
    // step 0 active → step body customers not hidden
    const customers = host.querySelector('[data-testid="step-body-customers"]');
    expect(customers?.classList.contains('hidden')).toBe(false);
  });

  // -------- submit --------

  it('submit() calls shipments-store.create (via wizardSubmit) and navigates to the detail page', async () => {
    render();
    const resp = makeResponse();
    storeMock.wizardSubmit.mockResolvedValue(resp);

    // Drive the submit through the confirm step's
    // onConfirmed handler — the orchestrator's submit()
    // calls confirmStep.confirm() which emits via the
    // confirmed output, which onConfirmed handles.
    const payload = {
      senderId: 's1',
      receiverId: 'r1',
      deliveryAddressId: 'addr-1',
      paymentType: 'PAGO_ORIGEN' as const,
      packages: [],
    };
    component.onConfirmed(payload);

    // Let the submit() promise chain resolve.
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(storeMock.wizardPatchData).toHaveBeenCalledWith(payload);
    expect(storeMock.wizardSubmit).toHaveBeenCalledTimes(1);
    expect(navigatedTo).toEqual(['/auth/shipments', resp.shipment.id]);
  });

  it('submit() sets errorMessage when the store rejects', async () => {
    render();
    storeMock.wizardSubmit.mockRejectedValueOnce(new Error('boom'));

    const payload = {
      senderId: 's1',
      receiverId: 'r1',
      deliveryAddressId: 'addr-1',
      paymentType: 'PAGO_ORIGEN' as const,
      packages: [],
    };
    component.onConfirmed(payload);
    await Promise.resolve();
    await Promise.resolve();
    await Promise.resolve();

    expect(component.errorMessage()).toBeTruthy();
    expect(navigatedTo).toBeNull();
  });

  it('submit() is a no-op when already submitting', async () => {
    render();
    component.isSubmitting.set(true);
    component.submit();
    expect(storeMock.wizardSubmit).not.toHaveBeenCalled();
  });

  // -------- cancel --------

  it('cancel() resets the wizard state and navigates to the shipments list', () => {
    render();
    component.cancel();
    expect(storeMock.wizardReset).toHaveBeenCalled();
    expect(navigatedTo).toEqual(['/auth/shipments']);
  });
});
