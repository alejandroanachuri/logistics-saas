import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpParams } from '@angular/common/http';
import { vi } from 'vitest';
import { of } from 'rxjs';

import { CompanyUsersService } from './company-users.service';
import {
  CompanyUserDetail,
  CompanyUserSummary,
  CreateCompanyUserRequest,
  CreateCompanyUserResponse,
  PageResponse,
  ResetPasswordResponse,
  Role,
  UpdateCompanyUserRequest,
} from '../types';

function makeSummary(id: string, username: string): CompanyUserSummary {
  return {
    id,
    username,
    email: `${username}@test.com`,
    firstName: username,
    lastName: null,
    status: 'ACTIVE',
    emailVerified: true,
    lastLoginAt: null,
    roles: [{ id: 'r-admin', name: 'COMPANY_ADMIN', description: null }],
    createdAt: '2026-01-01T00:00:00Z',
    isFirstAdmin: false,
  };
}

function makeDetail(id: string): CompanyUserDetail {
  return {
    ...makeSummary(id, 'user'),
    failedLoginAttempts: 0,
    lockedUntil: null,
    updatedAt: null,
  };
}

describe('CompanyUsersService', () => {
  let httpMock: { get: ReturnType<typeof vi.fn>; post: ReturnType<typeof vi.fn>; patch: ReturnType<typeof vi.fn> };
  let service: CompanyUsersService;

  beforeEach(() => {
    httpMock = { get: vi.fn(), post: vi.fn(), patch: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: httpMock },
        CompanyUsersService,
      ],
    });
    service = TestBed.inject(CompanyUsersService);
  });

  describe('list', () => {
    it('GETs /api/v1/company-users with page, size, sort as query params', () => {
      const resp: PageResponse<CompanyUserSummary> = {
        data: [makeSummary('u1', 'juan')],
        total: 1,
        page: 1,
        size: 10,
      };
      httpMock.get.mockReturnValue(of(resp));

      let emitted: PageResponse<CompanyUserSummary> | undefined;
      service.list({ page: 1, size: 10, sort: 'createdAt,desc' }).subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledTimes(1);
      const [url, options] = httpMock.get.mock.calls[0];
      expect(url).toBe('/api/v1/company-users');
      expect(options.params.get('page')).toBe('1');
      expect(options.params.get('size')).toBe('10');
      expect(options.params.get('sort')).toBe('createdAt,desc');
      expect(emitted).toEqual(resp);
    });

    it('includes optional filters (status, roleId, search) only when provided', () => {
      const resp: PageResponse<CompanyUserSummary> = { data: [], total: 0, page: 1, size: 10 };
      httpMock.get.mockReturnValue(of(resp));

      service.list({ status: 'DISABLED', roleId: 'r-admin', search: 'juan' }).subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.get('status')).toBe('DISABLED');
      expect(params.get('roleId')).toBe('r-admin');
      expect(params.get('search')).toBe('juan');
    });

    it('omits optional filters when not provided', () => {
      httpMock.get.mockReturnValue(of({ data: [], total: 0, page: 1, size: 10 }));

      service.list({ page: 1, size: 10 }).subscribe();

      const params: HttpParams = httpMock.get.mock.calls[0][1].params;
      expect(params.has('status')).toBe(false);
      expect(params.has('roleId')).toBe(false);
      expect(params.has('search')).toBe(false);
    });
  });

  describe('get', () => {
    it('GETs /api/v1/company-users/{id} and returns the detail', () => {
      const detail = makeDetail('u1');
      httpMock.get.mockReturnValue(of(detail));

      let emitted: CompanyUserDetail | undefined;
      service.get('u1').subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/company-users/u1');
      expect(emitted).toEqual(detail);
    });
  });

  describe('create', () => {
    it('POSTs the request body and returns the create response (with temporary password)', () => {
      const req: CreateCompanyUserRequest = {
        username: 'juan',
        email: 'juan@test.com',
        password: 'TmpP@ssw0rd!',
        roleIds: ['r-admin'],
      };
      const resp: CreateCompanyUserResponse = {
        user: makeDetail('u-new'),
        temporaryPassword: 'TmpP@ssw0rd!',
        passwordWarning: 'Compartila por un canal seguro.',
      };
      httpMock.post.mockReturnValue(of(resp));

      let emitted: CreateCompanyUserResponse | undefined;
      service.create(req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/company-users', req);
      expect(emitted).toEqual(resp);
    });
  });

  describe('update', () => {
    it('PATCHes /api/v1/company-users/{id} with the partial body and returns the detail', () => {
      const detail = makeDetail('u1');
      const req: UpdateCompanyUserRequest = { firstName: 'Juan Carlos', roleIds: ['r-admin'] };
      httpMock.patch.mockReturnValue(of(detail));

      let emitted: CompanyUserDetail | undefined;
      service.update('u1', req).subscribe((r) => (emitted = r));

      expect(httpMock.patch).toHaveBeenCalledWith('/api/v1/company-users/u1', req);
      expect(emitted).toEqual(detail);
    });
  });

  describe('disable', () => {
    it('POSTs /api/v1/company-users/{id}/disable and completes (void)', () => {
      httpMock.post.mockReturnValue(of(undefined));

      let emitted: void | undefined;
      service.disable('u1').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/company-users/u1/disable', null);
      expect(emitted).toBeUndefined();
    });
  });

  describe('reactivate', () => {
    it('POSTs /api/v1/company-users/{id}/reactivate and returns the detail', () => {
      const detail = makeDetail('u1');
      httpMock.post.mockReturnValue(of(detail));

      let emitted: CompanyUserDetail | undefined;
      service.reactivate('u1').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/company-users/u1/reactivate', null);
      expect(emitted).toEqual(detail);
    });
  });

  describe('resetPassword', () => {
    it('POSTs /api/v1/company-users/{id}/reset-password and returns the response (with temporary password)', () => {
      const resp: ResetPasswordResponse = {
        userId: 'u1',
        username: 'juan',
        temporaryPassword: 'NewP@ssw0rd!',
        passwordWarning: 'Compartila por un canal seguro.',
      };
      httpMock.post.mockReturnValue(of(resp));

      let emitted: ResetPasswordResponse | undefined;
      service.resetPassword('u1').subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/company-users/u1/reset-password', null);
      expect(emitted).toEqual(resp);
    });
  });
});
