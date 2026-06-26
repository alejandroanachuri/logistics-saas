import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { AddressesService } from './addresses.service';
import { Address, CreateAddressRequest, UpdateAddressRequest } from '../types';

function makeAddress(id: string): Address {
  return {
    id,
    street: 'Av. Corrientes',
    number: '1234',
    floor: '3',
    apartment: 'B',
    city: 'CABA',
    province: 'Buenos Aires',
    postalCode: 'C1043',
    reference: 'Timbre 3B',
    country: 'AR',
  };
}

describe('AddressesService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
    patch: ReturnType<typeof vi.fn>;
  };
  let service: AddressesService;

  beforeEach(() => {
    httpMock = { get: vi.fn(), post: vi.fn(), patch: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, AddressesService],
    });
    service = TestBed.inject(AddressesService);
  });

  describe('get', () => {
    it('GETs /api/v1/addresses/{id} and returns the address', () => {
      const address = makeAddress('a1');
      httpMock.get.mockReturnValue(of(address));

      let emitted: Address | undefined;
      service.get('a1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/addresses/a1');
      expect(emitted).toEqual(address);
    });
  });

  describe('create', () => {
    it('POSTs the request body and returns the freshly-created address', () => {
      const req: CreateAddressRequest = {
        street: 'Av. Corrientes',
        number: '1234',
        city: 'CABA',
        province: 'Buenos Aires',
        postalCode: 'C1043',
        country: 'AR',
      };
      const address = makeAddress('a-new');
      httpMock.post.mockReturnValue(of(address));

      let emitted: Address | undefined;
      service.create(req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/addresses', req);
      expect(emitted).toEqual(address);
    });
  });

  describe('update', () => {
    it('PATCHes /api/v1/addresses/{id} with the partial body and returns the address', () => {
      const address = makeAddress('a1');
      const req: UpdateAddressRequest = { floor: '4', reference: 'Timbre 4' };
      httpMock.patch.mockReturnValue(of(address));

      let emitted: Address | undefined;
      service.update('a1', req).subscribe((r) => (emitted = r));

      expect(httpMock.patch).toHaveBeenCalledWith('/api/v1/addresses/a1', req);
      expect(emitted).toEqual(address);
    });
  });
});
