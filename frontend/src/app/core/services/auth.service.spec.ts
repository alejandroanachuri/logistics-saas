import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { AuthService } from './auth.service';
import { AuthStore } from '../state/auth-store';
import { TenantStore } from '../state/tenant-store';
import {
  AuthUser,
  LoginRequest,
  LoginResponse,
  MeResponse,
  RegisterResponse,
  RegisterRequest,
  Tenant,
} from '../types';

/**
 * Helper — build a minimal AuthUser for store assertions.
 */
function makeUser(overrides: Partial<AuthUser> = {}): AuthUser {
  return {
    id: 'user-1',
    tenantId: 'tenant-1',
    tenantSlug: 'mvr',
    username: 'juan',
    email: 'juan@mvr.test',
    firstName: 'Juan',
    lastName: 'Perez',
    role: 'COMPANY_ADMIN',
    scope: 'COMPANY',
    emailVerified: false,
    ...overrides,
  };
}

function makeLoginResponse(overrides: Partial<LoginResponse> = {}): LoginResponse {
  return { user: makeUser(), expiresIn: 900, ...overrides };
}

function makeMeResponse(): MeResponse {
  return {
    user: {
      id: 'user-1',
      tenantId: 'tenant-1',
      tenantSlug: 'mvr',
      username: 'juan',
      role: 'COMPANY_ADMIN',
      scope: 'COMPANY',
      expiresIn: 900,
    },
  };
}

function makeRegisterResponse(): RegisterResponse {
  return {
    tenantId: 'tenant-1',
    slug: 'mvr',
    adminUserId: 'user-1',
    adminUsername: 'juan',
  };
}

function makeRegisterRequest(): RegisterRequest {
  return {
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
}

describe('AuthService', () => {
  let httpMock: {
    get: ReturnType<typeof vi.fn>;
    post: ReturnType<typeof vi.fn>;
  };
  let service: AuthService;
  let authStore: AuthStore;
  let tenantStore: TenantStore;

  beforeEach(() => {
    httpMock = {
      get: vi.fn(),
      post: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: HttpClient, useValue: httpMock },
        AuthService,
        AuthStore,
        TenantStore,
      ],
    });

    service = TestBed.inject(AuthService);
    authStore = TestBed.inject(AuthStore);
    tenantStore = TestBed.inject(TenantStore);
  });

  describe('login', () => {
    it('posts to /api/v1/auth/login with the credentials and returns the response', () => {
      const creds: LoginRequest = { slug: 'mvr', username: 'juan', password: 'Secret123!' };
      const resp = makeLoginResponse();
      httpMock.post.mockReturnValue(of(resp));

      let emitted: LoginResponse | undefined;
      service.login(creds).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledTimes(1);
      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/auth/login', creds);
      expect(emitted).toEqual(resp);
    });

    it('populates AuthStore.currentUser and TenantStore.currentTenant on success', () => {
      const creds: LoginRequest = { slug: 'mvr', username: 'juan', password: 'Secret123!' };
      const user = makeUser();
      httpMock.post.mockReturnValue(of(makeLoginResponse({ user })));

      service.login(creds).subscribe();

      expect(authStore.currentUser()).toEqual(user);
      expect(authStore.isAuthenticated()).toBe(true);
      const tenant: Tenant | null = tenantStore.currentTenant();
      expect(tenant).toEqual({ id: user.tenantId, slug: user.tenantSlug });
    });

    it('does not mutate the stores when the request fails', () => {
      const creds: LoginRequest = { slug: 'mvr', username: 'juan', password: 'wrong' };
      httpMock.post.mockReturnValue(
        throwError(
          () =>
            new HttpErrorResponse({
              status: 401,
              statusText: 'Unauthorized',
              error: { error: { code: 'INVALID_CREDENTIALS', message: 'bad' } },
            }),
        ),
      );

      service.login(creds).subscribe({ next: () => {}, error: () => {} });

      expect(authStore.currentUser()).toBeNull();
      expect(authStore.isAuthenticated()).toBe(false);
      expect(tenantStore.currentTenant()).toBeNull();
    });
  });

  describe('logout', () => {
    it('posts to /api/v1/auth/logout and clears both stores on success', () => {
      // Pre-populate stores as if a user is logged in.
      const user = makeUser();
      authStore.setUser(user);
      tenantStore.setTenant({ id: user.tenantId, slug: user.tenantSlug });
      httpMock.post.mockReturnValue(of(undefined));

      let emitted: void | undefined;
      service.logout().subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/auth/logout', null);
      expect(emitted).toBeUndefined();
      expect(authStore.currentUser()).toBeNull();
      expect(authStore.isAuthenticated()).toBe(false);
      expect(tenantStore.currentTenant()).toBeNull();
    });
  });

  describe('me', () => {
    it('gets /api/v1/auth/me and populates AuthStore + TenantStore on success', () => {
      const me = makeMeResponse();
      httpMock.get.mockReturnValue(of(me));

      let emitted: MeResponse | undefined;
      service.me().subscribe((r) => (emitted = r));

      expect(httpMock.get).toHaveBeenCalledWith('/api/v1/auth/me');
      expect(emitted).toEqual(me);
      // AuthStore gets a derived user from the me response.
      const user = authStore.currentUser();
      expect(user).toBeTruthy();
      expect(user?.id).toBe(me.user.id);
      expect(user?.tenantId).toBe(me.user.tenantId);
      expect(user?.tenantSlug).toBe(me.user.tenantSlug);
      expect(tenantStore.currentTenant()).toEqual({
        id: me.user.tenantId,
        slug: me.user.tenantSlug,
      });
    });
  });

  describe('refresh', () => {
    it('posts to /api/v1/auth/refresh and does NOT mutate the stores', () => {
      const resp = makeLoginResponse();
      httpMock.post.mockReturnValue(of(resp));

      let emitted: LoginResponse | undefined;
      service.refresh().subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/auth/refresh', null);
      expect(emitted).toEqual(resp);
      // refresh does not change the auth state — the interceptor
      // uses it as a one-shot cookie refresher.
      expect(authStore.currentUser()).toBeNull();
      expect(tenantStore.currentTenant()).toBeNull();
    });
  });

  describe('register', () => {
    it('posts to /api/v1/auth/register and returns the response', () => {
      const req = makeRegisterRequest();
      const resp = makeRegisterResponse();
      httpMock.post.mockReturnValue(of(resp));

      let emitted: RegisterResponse | undefined;
      service.register(req).subscribe((r) => (emitted = r));

      expect(httpMock.post).toHaveBeenCalledWith('/api/v1/auth/register', req);
      expect(emitted).toEqual(resp);
    });

    it('does NOT populate the auth/tenant stores on register (no auto-login)', () => {
      const req = makeRegisterRequest();
      const resp = makeRegisterResponse();
      httpMock.post.mockReturnValue(of(resp));

      service.register(req).subscribe();

      expect(authStore.currentUser()).toBeNull();
      expect(tenantStore.currentTenant()).toBeNull();
    });
  });
});
