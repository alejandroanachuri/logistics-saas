import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { vi } from 'vitest';

import { authGuard } from './auth-guard';
import { AuthStore } from '../state/auth-store';

describe('authGuard', () => {
  let authStore: AuthStore;
  let router: { parseUrl: ReturnType<typeof vi.fn>; navigate: ReturnType<typeof vi.fn> };

  function runGuard(url: string): unknown {
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, state),
    );
  }

  beforeEach(() => {
    router = {
      parseUrl: vi.fn((u: string) => ({ toString: () => u } as unknown as UrlTree)),
      navigate: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        AuthStore,
        { provide: Router, useValue: router },
      ],
    });
    authStore = TestBed.inject(AuthStore);
  });

  it('allows activation when the user is authenticated', () => {
    authStore.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: '',
      firstName: 'Juan',
      lastName: '',
      role: 'COMPANY_ADMIN',
      scope: 'COMPANY',
      emailVerified: false,
    });

    const result = runGuard('/dashboard');

    expect(result).toBe(true);
    expect(router.parseUrl).not.toHaveBeenCalled();
  });

  it('redirects to /login with returnUrl preserved when the user is unauthenticated', () => {
    // AuthStore starts empty by default.
    const tree = { toString: () => '/login?returnUrl=/dashboard' } as unknown as UrlTree;
    router.parseUrl.mockReturnValue(tree);

    const result = runGuard('/dashboard');

    expect(router.parseUrl).toHaveBeenCalledTimes(1);
    expect(router.parseUrl).toHaveBeenCalledWith('/login?returnUrl=/dashboard');
    expect(result).toBe(tree);
  });

  it('preserves nested returnUrl paths (e.g. /dashboard/profile)', () => {
    const tree = { toString: () => '/login?returnUrl=/dashboard/profile' } as unknown as UrlTree;
    router.parseUrl.mockReturnValue(tree);

    runGuard('/dashboard/profile');

    expect(router.parseUrl).toHaveBeenCalledWith('/login?returnUrl=/dashboard/profile');
  });
});
