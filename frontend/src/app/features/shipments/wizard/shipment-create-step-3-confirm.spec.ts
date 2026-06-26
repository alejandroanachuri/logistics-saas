import { TestBed, ComponentFixture } from '@angular/core/testing';
import { vi } from 'vitest';

import { ShipmentCreateStep3ConfirmComponent } from './shipment-create-step-3-confirm';
import { CustomerSummary } from '../../../core/types';
import { PackageDraft } from './shipment-create-step-2-packages';

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

function makeDraft(overrides: Partial<PackageDraft> = {}): PackageDraft {
  return {
    weightKg: 1.5,
    volumeCm3: null,
    dimensionsCm: '',
    contentDescription: 'Caja con libros',
    declaredValue: null,
    category: 'GENERAL',
    isFragile: false,
    isUrgent: false,
    requiresSignature: false,
    requiresIdCheck: false,
    ...overrides,
  };
}

describe('ShipmentCreateStep3ConfirmComponent', () => {
  let fixture: ComponentFixture<ShipmentCreateStep3ConfirmComponent>;
  let component: ShipmentCreateStep3ConfirmComponent;
  let emitSpy: ReturnType<typeof vi.spyOn>;

  function render(): void {
    TestBed.configureTestingModule({
      imports: [ShipmentCreateStep3ConfirmComponent],
    });
    fixture = TestBed.createComponent(ShipmentCreateStep3ConfirmComponent);
    component = fixture.componentInstance;
    emitSpy = vi.spyOn(component.confirmed, 'emit');
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render();
    expect(component).toBeTruthy();
  });

  // -------- inputs --------

  it('setSummary updates sender, receiver, packages, totalWeight', () => {
    render();
    component.setSummary(
      makeCustomer({ id: 's1', firstName: 'Remitente', lastName: 'A' }),
      makeCustomer({ id: 'r1', firstName: 'Destinatario', lastName: 'B' }),
      [makeDraft()],
    );
    expect(component.sender()?.id).toBe('s1');
    expect(component.receiver()?.id).toBe('r1');
    expect(component.packages().length).toBe(1);
    expect(component.totalWeight()).toBe(1.5);
  });

  // -------- submit --------

  it('confirm emits the assembled payload (sender/receiver/packages + deliveryAddressId + optional fields)', () => {
    render();
    component.setSummary(makeCustomer({ id: 's1' }), makeCustomer({ id: 'r1' }), [
      makeDraft({ weightKg: 2.0 }),
      makeDraft({ weightKg: 0.5 }),
    ]);
    component.deliveryAddressId.set('addr-1');
    component.deliveryInstructions.set('Dejar en portería');
    component.promisedDeliveryDate.set('2026-07-01');
    component.validateNow.set(true);
    component.confirm();
    expect(emitSpy).toHaveBeenCalledTimes(1);
    const payload = emitSpy.mock.calls[0]?.[0] as Record<string, unknown>;
    expect(payload['senderId']).toBe('s1');
    expect(payload['receiverId']).toBe('r1');
    expect(payload['deliveryAddressId']).toBe('addr-1');
    expect(payload['deliveryInstructions']).toBe('Dejar en portería');
    expect(payload['promisedDeliveryDate']).toBe('2026-07-01');
    expect(payload['validateNow']).toBe(true);
    const pkgs = payload['packages'] as Array<{ weightKg: number }>;
    expect(pkgs.length).toBe(2);
    expect(pkgs[0]?.weightKg).toBe(2.0);
    expect(pkgs[1]?.weightKg).toBe(0.5);
  });

  it('confirm does not emit when isSubmitting is true', () => {
    render();
    component.setSummary(makeCustomer({ id: 's1' }), makeCustomer({ id: 'r1' }), [makeDraft()]);
    component.deliveryAddressId.set('addr-1');
    component.isSubmitting.set(true);
    component.confirm();
    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('confirm does not emit when deliveryAddressId is empty', () => {
    render();
    component.setSummary(makeCustomer({ id: 's1' }), makeCustomer({ id: 'r1' }), [makeDraft()]);
    component.deliveryAddressId.set('');
    component.confirm();
    expect(emitSpy).not.toHaveBeenCalled();
  });

  it('isLoading mirrors isSubmitting', () => {
    render();
    expect(component.isLoading()).toBe(false);
    component.isSubmitting.set(true);
    expect(component.isLoading()).toBe(true);
    component.isSubmitting.set(false);
    expect(component.isLoading()).toBe(false);
  });

  // -------- display helpers --------

  it('displayName returns razonSocial for JURIDICA, full name otherwise', () => {
    render();
    expect(component.displayName(makeCustomer({ razonSocial: 'ACME S.A.' }))).toBe('ACME S.A.');
    expect(component.displayName(makeCustomer({ firstName: 'Juan', lastName: 'Pérez' }))).toBe(
      'Juan Pérez',
    );
  });
});
