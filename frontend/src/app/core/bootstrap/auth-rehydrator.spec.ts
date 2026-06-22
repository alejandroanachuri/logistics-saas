import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { of, tap, throwError } from 'rxjs';

import { AuthRehydrator } from './auth-rehydrator';
import { AuthService } from '../services/auth.service';
import { AuthStore } from '../state/auth-store';
import { TenantStore } from '../state/tenant-store';
import { AuthUser, MeResponse } from '../types';

/**
 * Tests for the cold-boot auth rehydrator.
 *
 * <p>The rehydrator runs at app bootstrap (via
 * {@code provideAppInitializer}) BEFORE the router evaluates
 * guards, so the {@code AuthStore} is populated by the time
 * {@code authGuard} / {@code teamAccessGuard} look at it.
 * Without this, deep-linking to {@code /auth/team} after
 * a page refresh redirected to {@code /login} even when valid
 * session cookies existed, because both guards saw an empty
 * {@code _currentUser} signal.
 *
 * <p>Coverage:
 * <ol>
 *   <li>Cold boot → {@code me()} is called and the stores are
 *       populated.</li>
 *   <li>Already-hydrated → {@code me()} is NOT called (no
 *       unnecessary request when the user navigates within
 *       an already-loaded app).</li>
 *   <li>401 from {@code me()} → stores stay empty, app
 *       bootstraps anyway.</li>
 *   <li>Network error from {@code me()} → stores stay empty,
 *       app bootstraps anyway.</li>
 * </ol>
 */
describe('AuthRehydrator', () => {
  let authService: { me: ReturnType<typeof vi.fn> };
  let authStore: AuthStore;
  let tenantStore: TenantStore;
  let rehydrator: AuthRehydrator;

  /**
   * Build a minimal MeResponse that hydrates a non-admin user.
   * The exact shape matches what the backend sends in PR-3+.
   */
  function makeMeResponse(overrides: Partial<MeResponse['user']> = {}): MeResponse {
    return {
      user: {
        id: 'user-1',
        tenantId: 'tenant-1',
        tenantSlug: 'mvr',
        username: 'juan',
        roles: ['COMPANY_OPERATOR'],
        scope: 'COMPANY',
        expiresIn: 900,
        ...overrides,
      },
    };
  }

  /**
   * Wire {@code authService.me()} to return the given response
   * AND mirror the real service's tap-side-effect on
   * {@code AuthStore} + {@code TenantStore}. This keeps the
   * stub honest (it must drive the same observable side effects
   * the production service does) so we test the rehydrator in
   * isolation without standing up the full {@code HttpClient}.
   */
  function stubMeReturning(me: MeResponse): void {
    authService.me.mockImplementation(() =>
      of(me).pipe(
        tap((resp) => {
          const user: AuthUser = {
            id: resp.user.id,
            tenantId: resp.user.tenantId,
            tenantSlug: resp.user.tenantSlug,
            username: resp.user.username,
            email: '',
            firstName: resp.user.username,
            lastName: '',
            role: '',
            roles: resp.user.roles ?? [],
            scope: resp.user.scope,
            emailVerified: true,
          };
          authStore.setUser(user);
          tenantStore.setTenant({ id: user.tenantId, slug: user.tenantSlug });
        }),
      ),
    );
  }

  beforeEach(() => {
    authService = {
      me: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        // The rehydrator is providedIn root, so we just inject
        // it. We supply a stub for AuthService so the test does
        // not need an HttpClient.
        { provide: AuthService, useValue: authService },
        AuthRehydrator,
        AuthStore,
        TenantStore,
      ],
    });

    authStore = TestBed.inject(AuthStore);
    tenantStore = TestBed.inject(TenantStore);
    rehydrator = TestBed.inject(AuthRehydrator);
  });

  it('calls authService.me() on cold boot (authStore has no user)', async () => {
    // Cold boot: store starts empty, /me returns a user.
    stubMeReturning(makeMeResponse());

    await rehydrator.rehydrate();

    expect(authService.me).toHaveBeenCalledTimes(1);
  });

  it('populates AuthStore + TenantStore after a successful rehydrate', async () => {
    stubMeReturning(makeMeResponse());

    // Pre-condition: store is empty.
    expect(authStore.isAuthenticated()).toBe(false);

    await rehydrator.rehydrate();

    // Post-condition: store is populated from the /me response.
    expect(authStore.isAuthenticated()).toBe(true);
    const user = authStore.currentUser();
    expect(user).toBeTruthy();
    expect(user?.id).toBe('user-1');
    expect(user?.tenantId).toBe('tenant-1');
    expect(user?.tenantSlug).toBe('mvr');
    expect(tenantStore.currentTenant()).toEqual({
      id: 'tenant-1',
      slug: 'mvr',
    });
  });

  it('leaves AuthStore unauthenticated when /me fails with 401', async () => {
    // Server says the session cookies are invalid → user must
    // NOT be marked as authenticated.
    authService.me.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 401,
            statusText: 'Unauthorized',
            error: { error: { code: 'UNAUTHENTICATED', message: 'bad' } },
          }),
      ),
    );

    await expect(rehydrator.rehydrate()).resolves.toBeUndefined();

    expect(authStore.isAuthenticated()).toBe(false);
    expect(authStore.currentUser()).toBeNull();
    expect(tenantStore.currentTenant()).toBeNull();
  });

  it('leaves AuthStore unauthenticated when /me fails with a network error (does not throw)', async () => {
    // Status 0 with a ProgressEvent-style error — connectivity
    // problem, not a server response.
    authService.me.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 0,
            statusText: 'Unknown Error',
            error: new ProgressEvent('error'),
          }),
      ),
    );

    // The rehydrator MUST swallow the error so the app still
    // bootstraps — the guards will then redirect to /login
    // because the store is empty.
    await expect(rehydrator.rehydrate()).resolves.toBeUndefined();

    expect(authStore.isAuthenticated()).toBe(false);
    expect(authStore.currentUser()).toBeNull();
    expect(tenantStore.currentTenant()).toBeNull();
  });

  it('does NOT call authService.me() again when the store is already hydrated', async () => {
    // Simulate the "user navigates within an already-loaded
    // app" case: AuthRehydrator.rehydrate() is called again
    // (e.g. a hot reload or future second-touchpoint) and the
    // store already has a user. We must skip the request.
    authStore.setUser({
      id: 'existing-user',
      tenantId: 'tenant-9',
      tenantSlug: 'existing',
      username: 'existing',
      email: '',
      firstName: 'Existing',
      lastName: 'User',
      role: 'COMPANY_ADMIN',
      roles: ['COMPANY_ADMIN'],
      scope: 'COMPANY',
      emailVerified: true,
    });

    await rehydrator.rehydrate();

    expect(authService.me).not.toHaveBeenCalled();
    // The pre-existing user must NOT be wiped by the skip path.
    expect(authStore.currentUser()?.id).toBe('existing-user');
  });
});