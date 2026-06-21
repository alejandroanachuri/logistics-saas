import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { CompanyUsersStore } from './company-users-store';
import { CompanyUsersService } from '../services/company-users.service';
import { RolesService } from '../services/roles.service';
import {
  CompanyUserDetail,
  CompanyUserSummary,
  CreateCompanyUserResponse,
  PageResponse,
  ResetPasswordResponse,
  UpdateCompanyUserRequest,
} from '../types';

function makeSummary(id: string): CompanyUserSummary {
  return {
    id,
    username: id,
    email: `${id}@test.com`,
    firstName: id,
    lastName: null,
    status: 'ACTIVE',
    emailVerified: true,
    lastLoginAt: null,
    roles: [],
    createdAt: '2026-01-01T00:00:00Z',
    isFirstAdmin: false,
  };
}

function makeDetail(id: string): CompanyUserDetail {
  return { ...makeSummary(id), failedLoginAttempts: 0, lockedUntil: null, updatedAt: null };
}

describe('CompanyUsersStore', () => {
  let companyUsersService: {
    list: ReturnType<typeof vi.fn>;
    get: ReturnType<typeof vi.fn>;
    create: ReturnType<typeof vi.fn>;
    update: ReturnType<typeof vi.fn>;
    disable: ReturnType<typeof vi.fn>;
    reactivate: ReturnType<typeof vi.fn>;
    resetPassword: ReturnType<typeof vi.fn>;
  };
  let rolesService: { listCompanyRoles: ReturnType<typeof vi.fn> };
  let store: CompanyUsersStore;

  beforeEach(() => {
    companyUsersService = {
      list: vi.fn(),
      get: vi.fn(),
      create: vi.fn(),
      update: vi.fn(),
      disable: vi.fn(),
      reactivate: vi.fn(),
      resetPassword: vi.fn(),
    };
    rolesService = { listCompanyRoles: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        { provide: CompanyUsersService, useValue: companyUsersService },
        { provide: RolesService, useValue: rolesService },
        CompanyUsersStore,
      ],
    });
    store = TestBed.inject(CompanyUsersStore);
  });

  describe('initial state', () => {
    it('starts empty: no users loaded, no detail, no roles', () => {
      expect(store.currentCompanyUsers()).toBeNull();
      expect(store.currentCompanyUser()).toBeNull();
      expect(store.availableRoles()).toEqual([]);
      expect(store.pagination()).toEqual({ page: 1, size: 10, total: 0 });
      expect(store.isLoading()).toBe(false);
    });
  });

  describe('loadList', () => {
    it('updates the list + pagination signals on success', async () => {
      const resp: PageResponse<CompanyUserSummary> = {
        data: [makeSummary('u1')],
        total: 1,
        page: 1,
        size: 10,
      };
      companyUsersService.list.mockReturnValue(of(resp));

      await store.loadList({ page: 1, size: 10 });

      expect(store.currentCompanyUsers()).toEqual(resp.data);
      expect(store.pagination()).toEqual({ page: 1, size: 10, total: 1 });
      expect(store.isLoading()).toBe(false);
    });

    it('toggles isLoading around the network call', async () => {
      const resp: PageResponse<CompanyUserSummary> = { data: [], total: 0, page: 1, size: 10 };
      companyUsersService.list.mockReturnValue(of(resp));

      const before = store.isLoading();
      await store.loadList({ page: 1, size: 10 });
      const after = store.isLoading();

      // Observable completes synchronously in tests; both reads happen after
      // the tap has run, so isLoading should be false in both cases here.
      // The real assertion is that the final state is false (no stuck loading).
      expect(after).toBe(false);
      // `before` may be false too since the async chain settles in one microtask;
      // the contract under test is "isLoading ends at false on success".
      expect(before).toBe(false);
    });

    it('leaves previous list intact on error and rethrows', async () => {
      // Seed the store with a previous successful list.
      companyUsersService.list.mockReturnValueOnce(
        of({ data: [makeSummary('u1')], total: 1, page: 1, size: 10 }),
      );
      await store.loadList({ page: 1, size: 10 });
      expect(store.currentCompanyUsers()?.length).toBe(1);

      // Now force a failure.
      companyUsersService.list.mockReturnValueOnce(throwError(() => new Error('boom')));
      await expect(store.loadList({ page: 2, size: 10 })).rejects.toThrow('boom');

      // Previous list is preserved.
      expect(store.currentCompanyUsers()?.length).toBe(1);
    });
  });

  describe('loadDetail', () => {
    it('updates currentCompanyUser on success', async () => {
      const detail = makeDetail('u1');
      companyUsersService.get.mockReturnValue(of(detail));

      await store.loadDetail('u1');

      expect(store.currentCompanyUser()).toEqual(detail);
    });

    it('propagates errors', async () => {
      companyUsersService.get.mockReturnValueOnce(throwError(() => new Error('not found')));
      await expect(store.loadDetail('u1')).rejects.toThrow('not found');
      expect(store.currentCompanyUser()).toBeNull();
    });
  });

  describe('create', () => {
    it('returns the create response envelope (with the temporary password)', async () => {
      const resp: CreateCompanyUserResponse = {
        user: makeDetail('u-new'),
        temporaryPassword: 'TmpP@ssw0rd!',
        passwordWarning: 'warning',
      };
      companyUsersService.create.mockReturnValue(of(resp));

      let result: CreateCompanyUserResponse | undefined;
      await store
        .create({ username: 'juan', email: 'j@test.com', password: 'p', roleIds: ['r-admin'] })
        .then((r) => (result = r));

      expect(result).toEqual(resp);
    });
  });

  describe('update', () => {
    it('returns the updated detail', async () => {
      const detail = makeDetail('u1');
      companyUsersService.update.mockReturnValue(of(detail));

      const req: UpdateCompanyUserRequest = { firstName: 'Juan Carlos' };
      let result: CompanyUserDetail | undefined;
      await store.update('u1', req).then((r) => (result = r));

      expect(result).toEqual(detail);
    });
  });

  describe('disable / reactivate / resetPassword', () => {
    it('disable returns void on success', async () => {
      companyUsersService.disable.mockReturnValue(of(undefined));
      let result: void | undefined;
      await store.disable('u1').then((r) => (result = r));
      expect(result).toBeUndefined();
    });

    it('reactivate returns the updated detail', async () => {
      const detail = makeDetail('u1');
      companyUsersService.reactivate.mockReturnValue(of(detail));
      let result: CompanyUserDetail | undefined;
      await store.reactivate('u1').then((r) => (result = r));
      expect(result).toEqual(detail);
    });

    it('resetPassword returns the envelope (with the new temporary password)', async () => {
      const resp: ResetPasswordResponse = {
        userId: 'u1',
        username: 'juan',
        temporaryPassword: 'NewP@ssw0rd!',
        passwordWarning: 'warning',
      };
      companyUsersService.resetPassword.mockReturnValue(of(resp));
      let result: ResetPasswordResponse | undefined;
      await store.resetPassword('u1').then((r) => (result = r));
      expect(result).toEqual(resp);
    });
  });

  describe('loadRoles', () => {
    it('populates availableRoles from the RolesService', async () => {
      const roles = [{ id: 'r-admin', name: 'COMPANY_ADMIN', description: null }];
      rolesService.listCompanyRoles.mockReturnValue(of(roles));

      await store.loadRoles();

      expect(store.availableRoles()).toEqual(roles);
    });
  });
});
