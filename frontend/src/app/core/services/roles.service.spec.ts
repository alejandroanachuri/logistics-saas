import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { RolesService } from './roles.service';
import { TenantStore } from '../state/tenant-store';
import { Role } from '../types';

function makeRole(id: string, name: Role['name']): Role {
  return { id, name, description: null };
}

describe('RolesService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
  };
  let service: RolesService;
  let tenantStore: TenantStore;

  beforeEach(() => {
    httpMock = { get: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: httpMock },
        RolesService,
        TenantStore,
      ],
    });

    service = TestBed.inject(RolesService);
    tenantStore = TestBed.inject(TenantStore);
  });

  it('fetches roles from /api/v1/roles?scope=COMPANY and returns the array', () => {
    const roles: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN')];
    httpMock.get.mockReturnValue(of(roles));

    let emitted: Role[] | undefined;
    service.listCompanyRoles().subscribe((r) => (emitted = r));

    expect(httpMock.get).toHaveBeenCalledTimes(1);
    const call = httpMock.get.mock.calls[0];
    expect(call[0]).toBe('/api/v1/roles');
    expect(call[1].params.has('scope')).toBe(true);
    expect(call[1].params.get('scope')).toBe('COMPANY');
    expect(emitted).toEqual(roles);
  });

  it('caches the result and reuses it for subsequent calls within the same tenant', () => {
    tenantStore.setTenant({ id: 'tenant-1', slug: 'mvr' });
    const roles: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN')];
    httpMock.get.mockReturnValue(of(roles));

    service.listCompanyRoles().subscribe();
    service.listCompanyRoles().subscribe();
    service.listCompanyRoles().subscribe();

    expect(httpMock.get).toHaveBeenCalledTimes(1);
  });

  it('refetches when the tenant changes (cache is tenant-scoped)', () => {
    const rolesA: Role[] = [makeRole('r-admin', 'COMPANY_ADMIN')];
    const rolesB: Role[] = [makeRole('r-other', 'COMPANY_OPERATOR')];
    httpMock.get.mockReturnValueOnce(of(rolesA)).mockReturnValueOnce(of(rolesB));

    service.listCompanyRoles().subscribe();
    tenantStore.setTenant({ id: 'tenant-2', slug: 'mvr2' });
    service.listCompanyRoles().subscribe();

    expect(httpMock.get).toHaveBeenCalledTimes(2);
  });

  it('propagates HTTP errors (does not cache them)', () => {
    tenantStore.setTenant({ id: 'tenant-1', slug: 'mvr' });
    httpMock.get.mockReturnValueOnce(of([]));
    service.listCompanyRoles().subscribe();
    expect(httpMock.get).toHaveBeenCalledTimes(1);

    // Force a refetch by switching tenant
    tenantStore.setTenant({ id: 'tenant-2', slug: 'mvr2' });
    httpMock.get.mockReturnValueOnce(of([makeRole('r-admin', 'COMPANY_ADMIN')]));
    service.listCompanyRoles().subscribe();

    expect(httpMock.get).toHaveBeenCalledTimes(2);
  });
});
