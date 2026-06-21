import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, ActivatedRouteSnapshot, RouterStateSnapshot } from '@angular/router';
import { vi } from 'vitest';

import { AuthStore } from '../state/auth-store';
import { authGuard } from '../guards/auth-guard';

describe('authStore (signal transitions)', () => {
  let store: AuthStore;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [AuthStore] });
    store = TestBed.inject(AuthStore);
  });

  it('starts empty: currentUser null, isAuthenticated false, isLoading false', () => {
    expect(store.currentUser()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(store.isLoading()).toBe(false);
  });

  it('setUser transitions to authenticated and clears isLoading', () => {
    store.setLoading(true);
    expect(store.isLoading()).toBe(true);

    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: '',
      firstName: 'Juan',
      lastName: '',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: false,
    });

    expect(store.currentUser()?.id).toBe('u1');
    expect(store.isAuthenticated()).toBe(true);
    expect(store.isLoading()).toBe(false);
  });

  it('clear() transitions back to empty and clears isLoading', () => {
    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: '',
      firstName: 'Juan',
      lastName: '',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: false,
    });
    store.setLoading(true);

    store.clear();

    expect(store.currentUser()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(store.isLoading()).toBe(false);
  });

  it('setLoading(true|false) flips the isLoading signal without touching the user', () => {
    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: '',
      firstName: 'Juan',
      lastName: '',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: false,
    });

    store.setLoading(true);
    expect(store.isLoading()).toBe(true);
    expect(store.currentUser()?.id).toBe('u1');

    store.setLoading(false);
    expect(store.isLoading()).toBe(false);
    expect(store.currentUser()?.id).toBe('u1');
  });

  /**
   * etapa-2-usuarios PR-4: the backend now returns
   * `roles: string[]` instead of a singular `role`. The store
   * exposes a `currentUserRoles` computed signal that returns
   * the array verbatim, and a `currentUserIsAdmin` computed
   * that is true when the array contains `COMPANY_ADMIN`.
   */
  it('currentUserRoles reflects the roles[] array from the wire response', () => {
    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'juan',
      email: '',
      firstName: 'Juan',
      lastName: '',
      role: 'COMPANY_OPERATOR',
      roles: ['COMPANY_OPERATOR', 'COMPANY_DRIVER'],
      scope: 'COMPANY',
      emailVerified: true,
    });

    expect(store.currentUserRoles()).toEqual(['COMPANY_OPERATOR', 'COMPANY_DRIVER']);
  });

  it('currentUserRoles is empty when no user is signed in', () => {
    expect(store.currentUserRoles()).toEqual([]);
  });

  it('currentUserIsAdmin is true when the user has COMPANY_ADMIN in roles[]', () => {
    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'admin',
      email: '',
      firstName: 'Admin',
      lastName: '',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN', 'COMPANY_VIEWER'],
      scope: 'COMPANY',
      emailVerified: true,
    });

    expect(store.currentUserIsAdmin()).toBe(true);
  });

  it('currentUserIsAdmin is false when the user has non-admin roles', () => {
    store.setUser({
      id: 'u1',
      tenantId: 't1',
      tenantSlug: 'mvr',
      username: 'driver',
      email: '',
      firstName: 'Driver',
      lastName: '',
      role: 'COMPANY_DRIVER',
      roles: ['COMPANY_DRIVER'],
      scope: 'COMPANY',
      emailVerified: true,
    });

    expect(store.currentUserIsAdmin()).toBe(false);
  });

  it('currentUserIsAdmin is false when no user is signed in', () => {
    expect(store.currentUserIsAdmin()).toBe(false);
  });
});

describe('authGuard', () => {
  let authStore: AuthStore;
  let router: { parseUrl: ReturnType<typeof vi.fn> };

  function runGuard(url: string): unknown {
    const state = { url } as RouterStateSnapshot;
    return TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, state),
    );
  }

  beforeEach(() => {
    router = { parseUrl: vi.fn((u: string) => ({ toString: () => u } as unknown as UrlTree)) };
    TestBed.configureTestingModule({
      providers: [AuthStore, { provide: Router, useValue: router }],
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
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: false,
    });

    const result = runGuard('/dashboard');

    expect(result).toBe(true);
    expect(router.parseUrl).not.toHaveBeenCalled();
  });

  it('redirects to /login with returnUrl preserved when the user is unauthenticated', () => {
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
