import { TestBed, ComponentFixture } from '@angular/core/testing';
import { vi } from 'vitest';

import { ShipmentCreateStep1CustomersComponent } from './shipment-create-step-1-customers';
import { CustomersService } from '../../../core/services/customers.service';
import { CustomerSummary } from '../../../core/types';
import { of } from 'rxjs';

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

describe('ShipmentCreateStep1CustomersComponent', () => {
  let fixture: ComponentFixture<ShipmentCreateStep1CustomersComponent>;
  let component: ShipmentCreateStep1CustomersComponent;
  let customersServiceMock: { list: ReturnType<typeof vi.fn> };

  function render(): void {
    customersServiceMock = {
      list: vi.fn().mockReturnValue(of({ data: [], page: 1, size: 20, total: 0 })),
    };
    TestBed.configureTestingModule({
      imports: [ShipmentCreateStep1CustomersComponent],
      providers: [{ provide: CustomersService, useValue: customersServiceMock }],
    });
    fixture = TestBed.createComponent(ShipmentCreateStep1CustomersComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render();
    expect(component).toBeTruthy();
  });

  // -------- header + inputs --------

  it('renders the H1 "Seleccioná remitente y destinatario"', () => {
    render();
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Seleccioná remitente y destinatario');
  });

  it('renders two search inputs (sender + receiver)', () => {
    render();
    const inputs = (fixture.nativeElement as HTMLElement).querySelectorAll('input[type="search"]');
    expect(inputs.length).toBe(2);
  });

  // -------- selection --------

  it('selectCustomer sets senderSelection when role is "sender"', () => {
    render();
    const c = makeCustomer({ id: 'cust-1', firstName: 'Juan', lastName: 'Pérez' });
    component.selectCustomer(c, 'sender');
    expect(component.senderSelection()?.id).toBe('cust-1');
    expect(component.receiverSelection()).toBeNull();
  });

  it('selectCustomer sets receiverSelection when role is "receiver"', () => {
    render();
    const c = makeCustomer({ id: 'cust-2', firstName: 'Ana', lastName: 'García' });
    component.selectCustomer(c, 'receiver');
    expect(component.receiverSelection()?.id).toBe('cust-2');
    expect(component.senderSelection()).toBeNull();
  });

  it('clearSelection resets the matching side', () => {
    render();
    const c1 = makeCustomer({ id: 'cust-1', firstName: 'Juan', lastName: 'Pérez' });
    const c2 = makeCustomer({ id: 'cust-2', firstName: 'Ana', lastName: 'García' });
    component.selectCustomer(c1, 'sender');
    component.selectCustomer(c2, 'receiver');
    component.clearSelection('sender');
    expect(component.senderSelection()).toBeNull();
    expect(component.receiverSelection()?.id).toBe('cust-2');
  });

  // -------- validation --------

  it('canAdvance returns false when sender is missing', () => {
    render();
    const c = makeCustomer({ id: 'cust-2' });
    component.selectCustomer(c, 'receiver');
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns false when receiver is missing', () => {
    render();
    const c = makeCustomer({ id: 'cust-1' });
    component.selectCustomer(c, 'sender');
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns false when sender === receiver (same id)', () => {
    render();
    const c = makeCustomer({ id: 'cust-1' });
    component.selectCustomer(c, 'sender');
    component.selectCustomer(c, 'receiver');
    expect(component.canAdvance()).toBe(false);
  });

  it('canAdvance returns true when both selected with different ids', () => {
    render();
    component.selectCustomer(makeCustomer({ id: 'cust-1' }), 'sender');
    component.selectCustomer(makeCustomer({ id: 'cust-2' }), 'receiver');
    expect(component.canAdvance()).toBe(true);
  });

  // -------- search --------

  it('searches the customers service with the typed query (debounced)', async () => {
    vi.useFakeTimers();
    try {
      render();
      customersServiceMock.list.mockReturnValue(
        of({ data: [makeCustomer()], page: 1, size: 20, total: 1 }),
      );
      const input = (fixture.nativeElement as HTMLElement).querySelector(
        'input[type="search"]',
      ) as HTMLInputElement;
      input.value = 'juan';
      input.dispatchEvent(new Event('input'));
      // Advance past the 300ms debounce.
      await vi.advanceTimersByTimeAsync(350);
      expect(customersServiceMock.list).toHaveBeenCalled();
      const args = customersServiceMock.list.mock.calls[0]?.[0] as { search?: string };
      expect(args.search).toBe('juan');
    } finally {
      vi.useRealTimers();
    }
  });

  // -------- display name helper --------

  it('displayName returns razonSocial for JURIDICA, full name otherwise', () => {
    render();
    expect(component.displayName(makeCustomer({ razonSocial: 'ACME S.A.' }))).toBe('ACME S.A.');
    expect(component.displayName(makeCustomer({ firstName: 'Juan', lastName: 'Pérez' }))).toBe(
      'Juan Pérez',
    );
  });
});
