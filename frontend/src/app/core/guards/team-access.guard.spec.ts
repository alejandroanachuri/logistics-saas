import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { vi } from 'vitest';

import { teamAccessGuard } from './team-access.guard';
import { AuthStore } from '../state/auth-store';
import { AuthUser } from '../types';

function userWithRoles(roles: string[]): AuthUser {
  return {
    id: 'u1',
    tenantId: 't1',
    tenantSlug: 'mvr',
    username: 'juan',
    email: '',
    firstName: 'Juan',
    lastName: '',
    role: roles[0] ?? '',
    roles,
    scope: 'COMPANY',
    emailVerified: true,
  };
}

describe('teamAccessGuard', () => {
  let authStore: AuthStore;
  let router: { parseUrl: ReturnType<typeof vi.fn> };

  function runGuard(url: string): unknown {
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      teamAccessGuard({} as ActivatedRouteSnapshot, state),
    );
  }

  beforeEach(() => {
    router = {
      parseUrl: vi.fn((u: string) => ({ toString: () => u } as unknown as UrlTree)),
    };
    TestBed.configureTestingModule({
      providers: [AuthStore, { provide: Router, useValue: router }],
    });
    authStore = TestBed.inject(AuthStore);
  });

  it('allows activation when the user is a COMPANY_ADMIN', () => {
    authStore.setUser(userWithRoles(['COMPANY_ADMIN']));

    const result = runGuard('/team');

    expect(result).toBe(true);
    expect(router.parseUrl).not.toHaveBeenCalled();
  });

  it('allows activation when the user has COMPANY_ADMIN among other roles', () => {
    authStore.setUser(userWithRoles(['COMPANY_OPERATOR', 'COMPANY_ADMIN']));

    const result = runGuard('/team');

    expect(result).toBe(true);
  });

  it('redirects to /auth/dashboard when the user is not a COMPANY_ADMIN', () => {
    authStore.setUser(userWithRoles(['COMPANY_OPERATOR']));

    const result = runGuard('/auth/team');

    expect(router.parseUrl).toHaveBeenCalledTimes(1);
    expect(router.parseUrl).toHaveBeenCalledWith('/auth/dashboard');
    expect(result).toBe(router.parseUrl.mock.results[0].value);
  });

  it('redirects to /auth/dashboard when no user is signed in', () => {
    // Store starts empty by default.
    const result = runGuard('/auth/team');

    expect(router.parseUrl).toHaveBeenCalledWith('/auth/dashboard');
    expect(result).toBe(router.parseUrl.mock.results[0].value);
  });
});
