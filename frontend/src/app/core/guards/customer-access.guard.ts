import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../state/auth-store';

/**
 * Functional {@code CanActivateFn} for the customer
 * management routes (`/auth/customers`, `/auth/customers/new`,
 * `/auth/customers/:id`, `/auth/customers/:id/edit`).
 *
 * <p>All authenticated company users — {@code COMPANY_ADMIN},
 * {@code COMPANY_OPERATOR}, {@code COMPANY_DRIVER}, and
 * {@code COMPANY_VIEWER} — can access the customer list.
 * The detail page's edit affordances are role-gated client-side
 * (the backend enforces server-side); the guard itself only
 * requires an authenticated session. Non-authenticated visitors
 * are bounced to the dashboard, mirroring the team guard's
 * "always present a sane fallback" pattern.
 *
 * <p>Note: this guard does NOT check {@code currentUserIsAdmin}.
 * Customers are operational data (sender / receiver for
 * shipments), not admin-only configuration. The team guard's
 * admin check would lock out operators who legitimately need
 * to pick a customer in the shipment form.
 *
 * <p>The guard does NOT perform a {@code /me} rehydration —
 * the auth layout handles that on mount. The store is the
 * source of truth.
 */
export const customerAccessGuard: CanActivateFn = (_route, _state) => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.currentUser()) {
    return true;
  }
  return router.parseUrl('/auth/dashboard');
};