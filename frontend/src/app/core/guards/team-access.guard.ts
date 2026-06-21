import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../state/auth-store';

/**
 * Functional {@code CanActivateFn} for the team management
 * routes (`/team`, `/team/new`, `/team/:id`, `/team/:id/edit`).
 *
 * <p>Reads `AuthStore.currentUserIsAdmin()` (the computed
 * signal added in PR-4 that returns true when the user has
 * `COMPANY_ADMIN` in their `roles[]` array). When true, the
 * guard returns `true`. Otherwise, redirects to `/dashboard`
 * so non-admin users cannot land on a 403 page or worse,
 * submit admin-only requests via the URL.
 *
 * <p>The guard does NOT perform a `/me` rehydration — the
 * auth layout handles that on mount. The store is the source
 * of truth.
 */
export const teamAccessGuard: CanActivateFn = (_route, _state) => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.currentUserIsAdmin()) {
    return true;
  }
  return router.parseUrl('/dashboard');
};
