import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

import { AuthStore } from '../state/auth-store';
import { TenantStore } from '../state/tenant-store';
import {
  AuthUser,
  LoginRequest,
  LoginResponse,
  MeResponse,
  RegisterRequest,
  RegisterResponse,
} from '../types';

/**
 * Typed wrapper over {@code HttpClient} for the authentication
 * endpoints. The {@code authInterceptor} already attaches
 * {@code withCredentials: true} to every request, so the
 * service does NOT pass credentials options explicitly.
 *
 * <p>Mutations to {@code AuthStore} / {@code TenantStore} live
 * here (and in the {@code errorInterceptor}'s forced-logout
 * branch). Components read the stores but never write them
 * directly.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly authStore = inject(AuthStore);
  private readonly tenantStore = inject(TenantStore);

  /**
   * {@code POST /api/v1/auth/login}. On success the response
   * is committed to {@code AuthStore} + {@code TenantStore}.
   */
  login(creds: LoginRequest): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/login', creds).pipe(
      tap((resp) => {
        this.authStore.setUser(resp.user);
        this.tenantStore.setTenant(this.tenantFromUser(resp.user));
      }),
    );
  }

  /**
   * {@code POST /api/v1/auth/logout}. On success the stores
   * are cleared. Errors propagate (the interceptor has already
   * done its bookkeeping) — the caller is responsible for
   * showing a toast if the call itself fails.
   */
  logout(): Observable<void> {
    return this.http.post<void>('/api/v1/auth/logout', null).pipe(
      tap(() => {
        this.authStore.clear();
        this.tenantStore.clear();
      }),
    );
  }

  /**
   * {@code GET /api/v1/auth/me}. On success the
   * {@code AuthUser}-shaped projection is committed to
   * {@code AuthStore} and the tenant projection to
   * {@code TenantStore}. Used by the {@code AuthenticatedLayout}
   * to rehydrate the user from a cold boot.
   */
  me(): Observable<MeResponse> {
    return this.http.get<MeResponse>('/api/v1/auth/me').pipe(
      tap((resp) => {
        const user = this.userFromMe(resp);
        this.authStore.setUser(user);
        this.tenantStore.setTenant(this.tenantFromUser(user));
      }),
    );
  }

  /**
   * {@code POST /api/v1/auth/refresh}. Used by the
   * {@code errorInterceptor} to renew the cookies. Does NOT
   * mutate the auth/tenant stores — the interceptor just
   * needs the side-effect of the Set-Cookie header on the
   * browser. Returned so tests and the rare manual call site
   * can still subscribe.
   */
  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/v1/auth/refresh', null);
  }

  /**
   * {@code POST /api/v1/auth/register}. Returns the
   * {@code RegisterResponse} but does NOT log the user in
   * automatically — the spec is explicit that the wizard
   * shows a success screen and the user clicks
   * "Ir a iniciar sesión" to navigate to {@code /login}.
   */
  register(payload: RegisterRequest): Observable<RegisterResponse> {
    return this.http.post<RegisterResponse>('/api/v1/auth/register', payload);
  }

  /**
   * Build the {@code Tenant} projection the dashboard
   * reads. F1 only needs {@code id} + {@code slug} (the
   * {@code legalName} is filled in by {@code TenantMeService}
   * in a later PR).
   */
  private tenantFromUser(user: AuthUser): { id: string; slug: string } {
    return { id: user.tenantId, slug: user.tenantSlug };
  }

  /**
   * Build the {@code AuthUser} projection the auth shell
   * reads from a {@code /me} response. The wire shape is a
   * subset of the full {@code AuthUser} (no {@code email},
   * {@code firstName}, {@code lastName} from {@code /me}
   * in v1; the layout only displays {@code firstName}, so
   * the missing fields default to the slug-derived username
   * for now).
   */
  private userFromMe(resp: MeResponse): AuthUser {
    return {
      id: resp.user.id,
      tenantId: resp.user.tenantId,
      tenantSlug: resp.user.tenantSlug,
      username: resp.user.username,
      email: '',
      firstName: resp.user.username,
      lastName: '',
      role: resp.user.role,
      scope: resp.user.scope,
      emailVerified: true,
    };
  }
}
