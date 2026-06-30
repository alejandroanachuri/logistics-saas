import { TestBed, ComponentFixture } from '@angular/core/testing';
import {
  provideRouter,
  ActivatedRoute,
  convertToParamMap,
  Router,
} from '@angular/router';
import { vi } from 'vitest';

import { CustomerEditComponent } from './customer-edit';
import { AuthStore } from '../../../core/state/auth-store';
import { CustomersStore } from '../../../core/state/customers-store';
import {
  AuthUser,
  Customer,
  UpdateCustomerRequest,
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

function makeDetail(overrides: Partial<Customer> = {}): Customer {
  return {
    id: 'c-target',
    personType: 'FISICA',
    firstName: 'Juan',
    lastName: 'Pérez',
    razonSocial: null,
    dni: '12345678',
    cuitCuil: null,
    taxCondition: 'CONSUMIDOR_FINAL',
    phone: '+5491100000000',
    email: 'juan@test.com',
    defaultAddressId: null,
    dataConsent: true,
    consentDate: '2026-01-01T00:00:00Z',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: null,
    ...overrides,
  };
}

describe('CustomerEditComponent', () => {
  let fixture: ComponentFixture<CustomerEditComponent>;
  let component: CustomerEditComponent;
  let customersStoreMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    currentCustomer: ReturnType<typeof vi.fn>;
    clearDetail: ReturnType<typeof vi.fn>;
  };
  let activatedRouteId = 'c-target';
  let navigatedTo: unknown[] | null = null;

  function render(currentUser: AuthUser | null, detail: Customer): void {
    customersStoreMock = {
      loadDetail: vi.fn().mockImplementation(async (id: string) => {
        expect(id).toBe(activatedRouteId);
        return detail;
      }),
      update: vi.fn().mockResolvedValue(detail),
      currentCustomer: vi.fn().mockReturnValue(detail),
      clearDetail: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [CustomerEditComponent],
      providers: [
        provideRouter([]),
        AuthStore,
        { provide: CustomersStore, useValue: customersStoreMock },
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
    fixture = TestBed.createComponent(CustomerEditComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    expect(component).toBeTruthy();
  });

  // -------- header --------

  it('renders the H1 "Editar cliente"', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Editar cliente');
  });

  // -------- pre-fill --------

  it('renders the shared customer form pre-filled with the customer record', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez', email: 'juan@test.com' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    // The shared form has the FISICA branch (default for FISICA).
    const dniInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[data-field="dni"]',
    ) as HTMLInputElement | null;
    expect(dniInput).toBeTruthy();
    expect(dniInput?.value).toBe('12345678');
    const firstNameInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[data-field="firstName"]',
    ) as HTMLInputElement | null;
    expect(firstNameInput?.value).toBe('Juan');
  });

  it('switches to the JURIDICA branch with razonSocial + cuitCuil pre-filled', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({
        personType: 'JURIDICA',
        firstName: null,
        lastName: null,
        razonSocial: 'ACME S.A.',
        dni: null,
        cuitCuil: '20123456789',
        email: 'contacto@acme.com',
        taxCondition: 'RESPONSABLE_INSCRIPTO',
      }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const cuitInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[data-field="cuitCuil"]',
    ) as HTMLInputElement | null;
    expect(cuitInput).toBeTruthy();
    expect(cuitInput?.value).toBe('20123456789');

    const razonSocialInput = (fixture.nativeElement as HTMLElement).querySelector(
      'input[data-field="razonSocial"]',
    ) as HTMLInputElement | null;
    expect(razonSocialInput?.value).toBe('ACME S.A.');
  });

  // -------- cancel link --------

  it('renders the "Cancelar" link pointing to /auth/customers/:id', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers/c-target"]',
    );
    expect(link).toBeTruthy();
    expect(link?.textContent).toContain('Cancelar');
  });

  // -------- save flow --------

  it('on successful submit calls store.update and navigates to /auth/customers/:id', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    component.onSubmit({
      personType: 'FISICA',
      firstName: 'María José',
      lastName: 'García',
      dni: '87654321',
      taxCondition: 'CONSUMIDOR_FINAL',
      phone: '+5491100000000',
      email: 'maria@test.com',
      dataConsent: true,
    });
    await Promise.resolve();
    await Promise.resolve();

    expect(customersStoreMock.update).toHaveBeenCalledTimes(1);
    const [idArg, payload] = customersStoreMock.update.mock.calls[0];
    expect(idArg).toBe('c-target');
    expect(payload as UpdateCustomerRequest).toEqual({
      firstName: 'María José',
      lastName: 'García',
      email: 'maria@test.com',
      dni: '87654321',
      cuitCuil: undefined,
      dataConsent: true,
    });

    expect(customersStoreMock.clearDetail).toHaveBeenCalled();
    expect(navigatedTo).toEqual(['/auth/customers', 'c-target']);
  });

  it('on JURIDICA submit, sends cuitCuil (no DNI / firstName / lastName)', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
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

    expect(customersStoreMock.update).toHaveBeenCalledTimes(1);
    const payload = customersStoreMock.update.mock.calls[0][1] as UpdateCustomerRequest;
    expect(payload.cuitCuil).toBe('20123456789');
    expect(payload.dni).toBeUndefined();
    expect(payload.firstName).toBeUndefined();
    expect(payload.lastName).toBeUndefined();

    expect(navigatedTo).toEqual(['/auth/customers', 'c-target']);
  });

  // -------- error handling --------

  it('renders an error message when update fails', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    customersStoreMock.update.mockRejectedValueOnce(new Error('boom'));
    component.onSubmit({
      personType: 'FISICA',
      firstName: 'Juan',
      lastName: 'Pérez',
      dni: '12345678',
      taxCondition: 'CONSUMIDOR_FINAL',
      phone: '+5491100000000',
      email: 'juan@test.com',
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
