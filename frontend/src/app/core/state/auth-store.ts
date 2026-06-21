import { Injectable, computed, signal } from '@angular/core';
import { AuthUser, Tenant } from '../types';

/**
 * Signal-based auth state. The single source of truth for
 * whether a user is logged in, who they are, and whether an
 * auth-related HTTP request is in flight.
 *
 * Mutations happen only through the explicit {@code setUser},
 * {@code clear}, and {@code setLoading} methods called from
 * {@code AuthService} (and from the {@code errorInterceptor}'s
 * forced-logout path). Components and the {@code authGuard}
 * read the {@code isAuthenticated} computed.
 *
 * Since {@code etapa-2-usuarios} the store exposes two
 * multi-role-aware computed signals:
 * - {@code currentUserRoles}: the full roles[] array
 * - {@code currentUserIsAdmin}: true if the user has the
 *   `COMPANY_ADMIN` role (drives {@code teamAccessGuard} and
 *   the "Equipo" sidebar item in PR-5).
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _currentUser = signal<AuthUser | null>(null);
  private readonly _isLoading = signal<boolean>(false);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

  /**
   * The full roles[] array from the backend (PR-3 contract).
   * Empty when no user is signed in. Multi-role consumers
   * (the team pages, the teamAccessGuard) read this rather
   * than the singular `role` field.
   */
  readonly currentUserRoles = computed<string[]>(
    () => this._currentUser()?.roles ?? [],
  );

  /**
   * True when the signed-in user has the `COMPANY_ADMIN` role.
   * Drives the "Equipo" sidebar item and the `teamAccessGuard`
   * in PR-5. False for anonymous, COMPANY_OPERATOR,
   * COMPANY_DRIVER, COMPANY_VIEWER, and PLATFORM-scope users.
   */
  readonly currentUserIsAdmin = computed<boolean>(
    () => this._currentUser()?.roles.includes('COMPANY_ADMIN') ?? false,
  );

  setUser(user: AuthUser): void {
    this._currentUser.set(user);
    this._isLoading.set(false);
  }

  clear(): void {
    this._currentUser.set(null);
    this._isLoading.set(false);
  }

  setLoading(value: boolean): void {
    this._isLoading.set(value);
  }
}
