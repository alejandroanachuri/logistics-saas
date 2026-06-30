import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { vi } from 'vitest';

import { CustomerCreateComponent } from './customer-create';
import { AuthStore } from '../../../core/state/auth-store';
import { CustomersStore } from '../../../core/state/customers-store';
import {
  AuthUser,
  CreateCustomerRequest,
} from '../../../core/types';

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

describe('CustomerCreateComponent', () => {
  let fixture: ComponentFixture<CustomerCreateComponent>;
  let component: CustomerCreateComponent;
  let customersStoreMock: {
    create: ReturnType<typeof vi.fn>;
  };
  let navigatedTo: unknown[] | null = null;

  function render(currentUserRoles: string[] | null): void {
    customersStoreMock = {
      create: vi.fn().mockResolvedValue({
        id: 'c-new',
        personType: 'FISICA',
        firstName: 'María',
        lastName: 'García',
        razonSocial: null,
        dni: '12345678',
        cuitCuil: null,
        taxCondition: 'CONSUMIDOR_FINAL',
        phone: '+5491100000000',
        email: 'maria@test.com',
        defaultAddressId: null,
        dataConsent: true,
        consentDate: '2026-06-26T00:00:00Z',
        createdAt: '2026-06-26T00:00:00Z',
        updatedAt: null,
      }),
    };

    TestBed.configureTestingModule({
      imports: [CustomerCreateComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CustomersStore, useValue: customersStoreMock },
      ],
    });

    const authStore = TestBed.inject(AuthStore);
    const router = TestBed.inject(Router);
    navigatedTo = null;
    vi.spyOn(router, 'navigate').mockImplementation(async (commands: readonly unknown[]) => {
      navigatedTo = [...commands];
      return true;
    });

    if (currentUserRoles !== null) authStore.setUser(makeUser(currentUserRoles));
    fixture = TestBed.createComponent(CustomerCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(['COMPANY_ADMIN']);
    expect(component).toBeTruthy();
  });

  // -------- header --------

  it('renders the H1 "Nuevo cliente"', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Nuevo cliente');
  });

  it('renders the "Datos personales" section heading', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const headings = (fixture.nativeElement as HTMLElement).querySelectorAll('h2');
    const text = Array.from(headings).map((h) => h.textContent?.trim() ?? '').join(' | ');
    expect(text).toContain('Datos personales');
  });

  // -------- cancel link --------

  it('renders the "Cancelar" link pointing to /auth/customers', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers"]',
    );
    expect(link).toBeTruthy();
    expect(link?.textContent).toContain('Cancelar');
  });

  // -------- shared form presence --------

  it('renders the shared customer form (app-customer-form element)', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const inner = (fixture.nativeElement as HTMLElement).querySelector(
      'app-customer-form',
    );
    expect(inner).toBeTruthy();
  });

  it('renders the FISICA branch with DNI input by default', () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    const dniInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[data-field="dni"]',
    );
    expect(dniInput).toBeTruthy();
  });

  // -------- submit flow --------

  it('on successful FISICA submit calls store.create and navigates to /auth/customers', async () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();

    component.onSubmit({
      personType: 'FISICA',
      firstName: 'María',
      lastName: 'García',
      dni: '12345678',
      taxCondition: 'CONSUMIDOR_FINAL',
      phone: '+5491100000000',
      email: 'maria@test.com',
      dataConsent: true,
    });
    await Promise.resolve();
    await Promise.resolve();

    expect(customersStoreMock.create).toHaveBeenCalledTimes(1);
    const payload = customersStoreMock.create.mock.calls[0][0] as CreateCustomerRequest;
    expect(payload.personType).toBe('FISICA');
    expect(payload.firstName).toBe('María');
    expect(payload.dni).toBe('12345678');
    expect(payload.dataConsent).toBe(true);

    expect(navigatedTo).toEqual(['/auth/customers']);
  });

  it('on successful JURIDICA submit sends razonSocial + cuitCuil', async () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();

    component.onSubmit({
      personType: 'JURIDICA',
      razonSocial: 'ACME S.A.',
      cuitCuil: '20123456789',
      taxCondition: 'RESPONSABLE_INSCRIPTO',
      phone: '+5491100000000',
      email: 'contacto@acme.com',
      dataConsent: true,
    });
    await Promise.resolve();
    await Promise.resolve();

    expect(customersStoreMock.create).toHaveBeenCalledTimes(1);
    const payload = customersStoreMock.create.mock.calls[0][0] as CreateCustomerRequest;
    expect(payload.personType).toBe('JURIDICA');
    expect(payload.razonSocial).toBe('ACME S.A.');
    expect(payload.cuitCuil).toBe('20123456789');
    expect(payload.taxCondition).toBe('RESPONSABLE_INSCRIPTO');
    expect(payload.dataConsent).toBe(true);

    expect(navigatedTo).toEqual(['/auth/customers']);
  });

  it('renders an error message when create fails', async () => {
    render(['COMPANY_ADMIN']);
    fixture.detectChanges();
    customersStoreMock.create.mockRejectedValueOnce(new Error('boom'));

    component.onSubmit({
      personType: 'FISICA',
      firstName: 'María',
      lastName: 'García',
      dni: '12345678',
      taxCondition: 'CONSUMIDOR_FINAL',
      phone: '+5491100000000',
      email: 'maria@test.com',
      dataConsent: true,
    });
    await Promise.resolve();
    await Promise.resolve();

    fixture.detectChanges();
    const errorEl = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="error"]',
    );
    expect(errorEl).toBeTruthy();
    expect(component.errorMessage()).toBeTruthy();
    expect(navigatedTo).toBeNull();
  });
});
