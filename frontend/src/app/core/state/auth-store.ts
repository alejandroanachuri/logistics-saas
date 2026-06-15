import { Injectable, computed, signal } from '@angular/core';
import { inject, Injector, runInInjectionContext } from '@angular/core';
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
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _currentUser = signal<AuthUser | null>(null);
  private readonly _isLoading = signal<boolean>(false);

  readonly currentUser = this._currentUser.asReadonly();
  readonly isLoading = this._isLoading.asReadonly();
  readonly isAuthenticated = computed(() => this._currentUser() !== null);

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
