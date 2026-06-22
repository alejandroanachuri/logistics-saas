import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../state/auth-store';

/**
 * Functional {@code CanActivateFn} for routes that require
 * an authenticated user (the `auth` parent route covers
 * {@code /auth/dashboard} + {@code /auth/team/*}).
 *
 * <p>Reads {@code AuthStore.isAuthenticated()}. The store is
 * the single source of truth — we do NOT call {@code /me}
 * from the guard, the {@code AuthenticatedLayoutComponent}
 * handles rehydration on mount.
 *
 * <p>When the store reports unauthenticated, the guard
 * returns a {@code UrlTree} for {@code /login} with the
 * original URL preserved in the {@code returnUrl} query
 * parameter so the login page can bounce the user back
 * after a successful login.
 */
export const authGuard: CanActivateFn = (_route, state) => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.isAuthenticated()) {
    return true;
  }
  return router.parseUrl(`/login?returnUrl=${state.url}`);
};
