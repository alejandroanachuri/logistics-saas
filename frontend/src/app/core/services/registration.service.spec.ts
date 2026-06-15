import { TestBed } from '@angular/core/testing';
import { HttpClient } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { RegistrationService } from './registration.service';
import { RegisterRequest, RegisterResponse } from '../types';

describe('RegistrationService', () => {
  let httpMock: {
    post: ReturnType<typeof vi.fn>;
  };
  let service: RegistrationService;

  beforeEach(() => {
    httpMock = { post: vi.fn() };
    TestBed.configureTestingModule({
      providers: [{ provide: HttpClient, useValue: httpMock }, RegistrationService],
    });
    service = TestBed.inject(RegistrationService);
  });

  it('POSTs the payload to /api/v1/auth/register and returns the response', () => {
    const payload: RegisterRequest = {
      company: {
        legalName: 'MVR SA',
        cuit: '30-71234567-8',
        taxType: 'RESPONSABLE_INSCRIPTO',
        slug: 'mvr',
        contactEmail: 'admin@mvr.test',
        address: {
          country: 'AR',
          province: 'BUENOS_AIRES',
          city: 'La Plata',
          line: 'Calle',
          number: '123',
          postalCode: '1900',
        },
      },
      admin: {
        firstName: 'Juan',
        lastName: 'Perez',
        username: 'juan',
        email: 'juan@mvr.test',
        password: 'Secret123!',
        passwordConfirmation: 'Secret123!',
      },
      acceptsTerms: true,
      acceptsPrivacy: true,
    };
    const resp: RegisterResponse = {
      tenantId: 'tenant-1',
      slug: 'mvr',
      adminUserId: 'user-1',
      adminUsername: 'juan',
    };
    httpMock.post.mockReturnValue(of(resp));

    let emitted: RegisterResponse | undefined;
    service.submit(payload).subscribe((r) => (emitted = r));

    expect(httpMock.post).toHaveBeenCalledTimes(1);
    expect(httpMock.post).toHaveBeenCalledWith('/api/v1/auth/register', payload);
    expect(emitted).toEqual(resp);
  });
});
