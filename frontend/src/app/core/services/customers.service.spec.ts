import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { CustomersService } from './customers.service';
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

describe('CustomersService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
  };
  let service: CustomersService;

  beforeEach(() => {
    httpMock = { get: vi.fn(), post: vi.fn(), patch: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, CustomersService],
    });
    service = TestBed.inject(CustomersService);
  });

  describe('list', () => {
    it('GETs /api/v1/customers with page, size, sort as query params', () => {
      const resp: PageResponse<CustomerSummary> = {
        data: [makeSummary('c1')],
        total: 1,
        page: 1,
        size: 10,
      };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: PageResponse<CustomerSummary> | undefined;
      service.list({ page: 1, size: 10, sort: 'createdAt,desc' }).subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledTimes(1);
      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/customers');
      expect(options.params.get('page')).toBe('1');
      expect(options.params.get('size')).toBe('10');
      expect(options.params.get('sort')).toBe('createdAt,desc');
      expect(emitted).toEqual(resp);
    });

    it('includes search + status filters only when provided', () => {
      httpMock.get.mockReturnValue(of({ data: [], total: 0, page: 1, size: 10 }));

      service.list({ search: 'juan', status: 'ACTIVE' }).subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.get('search')).toBe('juan');
      expect(params.get('status')).toBe('ACTIVE');
    });

    it('omits optional filters when not provided', () => {
      httpMock.get.mockReturnValue(of({ data: [], total: 0, page: 1, size: 10 }));

      service.list({ page: 1, size: 10 }).subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.has('search')).toBe(false);
      expect(params.has('status')).toBe(false);
    });
  });

  describe('get', () => {
    it('GETs /api/v1/customers/{id} and returns the detail', () => {
      const detail = makeDetail('c1');
      httpMock.get.mockReturnValue(of(detail));

      let emitted: Customer | undefined;
      service.get('c1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/customers/c1');
      expect(emitted).toEqual(detail);
    });
  });

  describe('create', () => {
    it('POSTs the request body and returns the detail', () => {
      const req: CreateCustomerRequest = {
        personType: 'FISICA',
        firstName: 'Juan',
        lastName: 'Pérez',
        taxCondition: 'CONSUMIDOR_FINAL',
        phone: '+541112345678',
        email: 'juan@test.com',
        dataConsent: true,
      };
      const detail = makeDetail('c-new');
      httpMock.post.mockReturnValue(of(detail));

      let emitted: Customer | undefined;
      service.create(req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/customers', req);
      expect(emitted).toEqual(detail);
    });
  });

  describe('update', () => {
    it('PATCHes /api/v1/customers/{id} with the partial body and returns the detail', () => {
      const detail = makeDetail('c1');
      const req: UpdateCustomerRequest = { firstName: 'Juan Carlos', email: 'juan.c@test.com' };
      httpMock.patch.mockReturnValue(of(detail));

      let emitted: Customer | undefined;
      service.update('c1', req).subscribe((r) => (emitted = r));

      expect(httpMock.patch).toHaveBeenCalledWith('/api/v1/customers/c1', req);
      expect(emitted).toEqual(detail);
    });
  });

  describe('disable', () => {
    it('POSTs /api/v1/customers/{id}/disable and completes (void)', () => {
      httpMock.post.mockReturnValue(of(undefined));

      let emitted: void | undefined;
      service.disable('c1').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/customers/c1/disable', null);
      expect(emitted).toBeUndefined();
    });
  });
});
