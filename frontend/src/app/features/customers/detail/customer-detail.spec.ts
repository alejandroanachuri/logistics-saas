import { TestBed, ComponentFixture } from '@angular/core/testing';
import {
  provideRouter,
  ActivatedRoute,
  convertToParamMap,
  Router,
} from '@angular/router';
import { vi } from 'vitest';

import { CustomerDetailComponent } from './customer-detail';
import { AuthStore } from '../../../core/state/auth-store';
import { CustomersStore } from '../../../core/state/customers-store';
import { AuthUser, Customer } from '../../../core/types';

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

describe('CustomerDetailComponent', () => {
  let fixture: ComponentFixture<CustomerDetailComponent>;
  let component: CustomerDetailComponent;
  let customersStoreMock: {
    loadDetail: ReturnType<typeof vi.fn>;
    disable: ReturnType<typeof vi.fn>;
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
      disable: vi.fn().mockResolvedValue(undefined),
      clearDetail: vi.fn(),
    };

    TestBed.configureTestingModule({
      imports: [CustomerDetailComponent],
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
    fixture = TestBed.createComponent(CustomerDetailComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  // -------- warm-up --------

  it('warm-up — component instantiates', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();
    expect(component).toBeTruthy();
  });

  // -------- header --------

  it('renders the H1 with the customer full name', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({ firstName: 'Juan', lastName: 'Pérez' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('Juan');
    expect(h1?.textContent).toContain('Pérez');
  });

  it('renders the razon social as H1 for JURIDICA customers', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({
        personType: 'JURIDICA',
        firstName: null,
        lastName: null,
        razonSocial: 'ACME S.A.',
        dni: null,
        cuitCuil: '20123456789',
      }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const h1 = (fixture.nativeElement as HTMLElement).querySelector('h1');
    expect(h1?.textContent).toContain('ACME S.A.');
  });

  // -------- back link --------

  it('renders the "Volver a clientes" back link', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector(
      'a[href="/auth/customers"]',
    );
    expect(link).toBeTruthy();
  });

  // -------- field-level security --------

  it('shows full DNI for COMPANY_ADMIN', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail({ dni: '12345678' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('12345678');
  });

  it('masks DNI for COMPANY_VIEWER', async () => {
    render(
      makeUser(['COMPANY_VIEWER']),
      makeDetail({ dni: '12345678' }),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('12***78');
    expect(text).not.toContain('12345678');
  });

  // -------- edit button gating --------

  it('shows the "Editar información" button for COMPANY_ADMIN', async () => {
    render(makeUser(['COMPANY_ADMIN']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="edit-info-button"]',
    );
    expect(btn).toBeTruthy();
    expect((btn as HTMLButtonElement | null)?.disabled).toBe(false);
  });

  it('shows the "Editar información" button for COMPANY_OPERATOR', async () => {
    render(makeUser(['COMPANY_OPERATOR']), makeDetail());
    await Promise.resolve();
    fixture.detectChanges();

    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="edit-info-button"]',
    );
    expect(btn).toBeTruthy();
    expect((btn as HTMLButtonElement | null)?.disabled).toBe(false);
  });

  // -------- disable button gating --------

  it('hides the "Desactivar" button for COMPANY_OPERATOR (admin-only action)', async () => {
    render(
      makeUser(['COMPANY_OPERATOR']),
      makeDetail(),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="disable-customer-button"]',
    );
    expect(btn).toBeNull();
  });

  it('shows the "Desactivar" button for COMPANY_ADMIN', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail(),
    );
    await Promise.resolve();
    fixture.detectChanges();

    const btn = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="disable-customer-button"]',
    ) as HTMLButtonElement | null;
    expect(btn).toBeTruthy();
  });

  // -------- disable flow --------

  it('shows the disable confirm dialog when "Desactivar" is clicked', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail(),
    );
    await Promise.resolve();
    fixture.detectChanges();

    component.showDisableConfirm.set(true);
    fixture.detectChanges();

    const dialog = (fixture.nativeElement as HTMLElement).querySelector(
      'app-confirm-dialog',
    );
    expect(dialog).toBeTruthy();
  });

  it('calls store.disable with the target id when the disable dialog is confirmed', async () => {
    render(
      makeUser(['COMPANY_ADMIN']),
      makeDetail(),
    );
    await Promise.resolve();
    fixture.detectChanges();

    await component.disable();
    await Promise.resolve();

    expect(customersStoreMock.disable).toHaveBeenCalledWith('c-target');
  });
});
