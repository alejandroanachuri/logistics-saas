import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { AuthService } from '../services/auth.service';
import { AuthStore } from '../state/auth-store';
import { TenantStore } from '../state/tenant-store';

/**
 * Rehydrates the {@code AuthStore} and {@code TenantStore}
 * from the session cookies set by the backend at login time.
 *
 * <p>Runs at app bootstrap (via {@code provideAppInitializer})
 * so the auth state is populated BEFORE the router starts
 * evaluating guards. Without this, navigating directly to
 * {@code /dashboard/team} after a page refresh would hit
 * {@code authGuard} + {@code teamAccessGuard} with an empty
 * {@code _currentUser} signal and redirect to
 * {@code /login} even though the user is still authenticated.
 *
 * <p>Behavior:
 * <ul>
 *   <li>If the store is already hydrated, do nothing (skip
 *       the rehydrate to avoid an unnecessary request).</li>
 *   <li>Call {@code AuthService.me()}. On 200, the service
 *       populates both stores internally (existing behavior).</li>
 *   <li>On any error (401, 403, network), clear the stores
 *       and return without throwing. The guards will then
 *       redirect to {@code /login} (or stay on a public page)
 *       correctly.</li>
 * </ul>
 */
@Injectable({ providedIn: 'root' })
export class AuthRehydrator {
  private readonly authService = inject(AuthService);
  private readonly authStore = inject(AuthStore);
  private readonly tenantStore = inject(TenantStore);

  async rehydrate(): Promise<void> {
    if (this.authStore.isAuthenticated()) {
      return;
    }
    try {
      await firstValueFrom(this.authService.me());
    } catch {
      // Failure: clear stores so guards behave correctly.
      this.authStore.clear();
      this.tenantStore.clear();
      // Do not throw — the app must still bootstrap even if
      // the user is unauthenticated.
    }
  }
}