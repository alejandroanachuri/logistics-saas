import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { CustomersStore } from './customers-store';
import { CustomersService } from '../services/customers.service';
import {
  Customer,
  CustomerSummary,
  CreateCustomerRequest,
  PageResponse,
  UpdateCustomerRequest,
} from '../types';

function makeSummary(id: string): CustomerSummary {
  return {
    id,
    firstName: 'Juan',
    lastName: 'Pérez',
    razonSocial: null,
    email: 'juan@test.com',
    phone: '+541112345678',
    dni: '12***56',
    cuitCuil: null,
    taxCondition: 'CONSUMIDOR_FINAL',
    status: 'ACTIVE',
    createdAt: '2026-01-01T00:00:00Z',
  };
}

function makeDetail(id: string): Customer {
  return {
    ...makeSummary(id),
    personType: 'FISICA',
    defaultAddressId: null,
    dataConsent: true,
    consentDate: '2026-01-01T00:00:00Z',
    updatedAt: null,
  };
}

describe('CustomersStore', () => {
  let customersService: {
    list: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    disable: ReturnType<typeof vi.fn>;
  };
  let store: CustomersStore;

  beforeEach(() => {
    customersService = {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      disable: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [{ provide: CustomersService, useValue: customersService }, CustomersStore],
    });
    store = TestBed.inject(CustomersStore);
  });

  describe('initial state', () => {
    it('starts empty: no customers, no detail, no filters', () => {
      expect(store.currentCustomers()).toBeNull();
      expect(store.currentCustomer()).toBeNull();
      expect(store.listFilters()).toEqual({});
      expect(store.pagination()).toEqual({ page: 1, size: 20, total: 0 });
      expect(store.isLoading()).toBe(false);
      expect(store.isSubmitting()).toBe(false);
    });
  });

  describe('loadList', () => {
    it('updates the list + pagination signals on success', async () => {
      const resp: PageResponse<CustomerSummary> = {
        data: [makeSummary('c1')],
        total: 1,
        page: 1,
        size: 20,
      };
      customersService.list.mockReturnValue(of(resp));

      await store.loadList({ page: 1, size: 20, search: 'juan' });

      expect(store.currentCustomers()).toEqual(resp.data);
      expect(store.pagination()).toEqual({ page: 1, size: 20, total: 1 });
      expect(store.listFilters()).toEqual({ search: 'juan' });
      expect(store.isLoading()).toBe(false);
    });

    it('leaves previous list intact on error and rethrows', async () => {
      customersService.list.mockReturnValueOnce(
        of({ data: [makeSummary('c1')], total: 1, page: 1, size: 20 }),
      );
      await store.loadList({ page: 1, size: 20 });
      expect(store.currentCustomers()?.length).toBe(1);

      customersService.list.mockReturnValueOnce(throwError(() => new Error('boom')));
      await expect(store.loadList({ page: 2, size: 20 })).rejects.toThrow('boom');

      expect(store.currentCustomers()?.length).toBe(1);
    });
  });

  describe('loadDetail', () => {
    it('updates currentCustomer on success', async () => {
      const detail = makeDetail('c1');
      customersService.get.mockReturnValue(of(detail));

      await store.loadDetail('c1');

      expect(store.currentCustomer()).toEqual(detail);
    });

    it('propagates errors', async () => {
      customersService.get.mockReturnValueOnce(throwError(() => new Error('not found')));
      await expect(store.loadDetail('c1')).rejects.toThrow('not found');
      expect(store.currentCustomer()).toBeNull();
    });
  });

  describe('create', () => {
    it('returns the created detail and clears isSubmitting on success', async () => {
      const detail = makeDetail('c-new');
      customersService.create.mockReturnValue(of(detail));

      const req: CreateCustomerRequest = {
        personType: 'FISICA',
        firstName: 'Juan',
        lastName: 'Pérez',
        taxCondition: 'CONSUMIDOR_FINAL',
        phone: '+541112345678',
        dataConsent: true,
      };
      let result: Customer | undefined;
      await store.create(req).then((r) => (result = r));

      expect(result).toEqual(detail);
      expect(store.isSubmitting()).toBe(false);
    });
  });

  describe('update', () => {
    it('returns the updated detail and syncs the cached currentCustomer', async () => {
      const detail = makeDetail('c1');
      customersService.get.mockReturnValue(of(detail));
      await store.loadDetail('c1');

      const updated: Customer = { ...detail, firstName: 'Juan Carlos' };
      customersService.update.mockReturnValue(of(updated));

      const req: UpdateCustomerRequest = { firstName: 'Juan Carlos' };
      let result: Customer | undefined;
      await store.update('c1', req).then((r) => (result = r));

      expect(result).toEqual(updated);
      expect(store.currentCustomer()).toEqual(updated);
    });
  });

  describe('disable', () => {
    it('returns void and clears isSubmitting on success', async () => {
      customersService.disable.mockReturnValue(of(undefined));

      let result: void | undefined;
      await store.disable('c1').then((r) => (result = r));

      expect(result).toBeUndefined();
      expect(store.isSubmitting()).toBe(false);
    });
  });

  describe('clearDetail', () => {
    it('resets currentCustomer to null', async () => {
      customersService.get.mockReturnValue(of(makeDetail('c1')));
      await store.loadDetail('c1');

      store.clearDetail();

      expect(store.currentCustomer()).toBeNull();
    });
  });
});
