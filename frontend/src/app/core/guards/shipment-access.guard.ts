import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from '../state/auth-store';

/**
 * Functional {@code CanActivateFn} for the shipment
 * management routes (`/auth/shipments`, `/auth/shipments/new`,
 * `/auth/shipments/:id`, `/auth/shipments/:id/edit`,
 * `/auth/shipments/:id/events`).
 *
 * <p>All authenticated company users — {@code COMPANY_ADMIN},
 * {@code COMPANY_OPERATOR}, {@code COMPANY_DRIVER}, and
 * {@code COMPANY_VIEWER} — can access the shipment list and
 * detail pages. State-changing affordances (create, record
 * event, cancel) are role-gated client-side and enforced
 * server-side by the backend's RBAC checks; the guard itself
 * only requires an authenticated session.
 *
 * <p>Note: this guard does NOT check {@code currentUserIsAdmin}.
 * Shipments are the core operational entity of the platform —
 * locking operators out would defeat the purpose of the
 * "operator at the branch creates the shipment" workflow.
 *
 * <p>Note: the public tracking portal route
 * (`/track/:lgstId`) is OUTSIDE the {@code /auth/} layout and
 * uses a separate guard (or no guard — the public portal is
 * open to anonymous visitors). This guard only protects
 * company-internal shipment routes.
 *
 * <p>The guard does NOT perform a {@code /me} rehydration —
 * the auth layout handles that on mount. The store is the
 * source of truth.
 */
export const shipmentAccessGuard: CanActivateFn = (_route, _state) => {
  const authStore = inject(AuthStore);
  const router = inject(Router);

  if (authStore.currentUser()) {
    return true;
  }
  return router.parseUrl('/auth/dashboard');
};