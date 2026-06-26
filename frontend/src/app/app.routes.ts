import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth-guard';
import { teamAccessGuard } from './core/guards/team-access.guard';
import { customerAccessGuard } from './core/guards/customer-access.guard';
import { shipmentAccessGuard } from './core/guards/shipment-access.guard';

/**
 * F1 + gap-#5 + etapa-2-usuarios routes. Two top-level
 * regions:
 * - Public for ''/login/register/terms/privacy (lazy leaves).
 * - Authenticated behind the `auth` parent which mounts
 *   {@code AuthLayoutComponent} (the workspace shell).
 *
 * <p>The `auth` parent hosts two sibling feature surfaces:
 * - `dashboard` — the default landing page after login.
 * - `team` — the user-management surface (etapa-2-usuarios
 *   PR-5), gated by {@code teamAccessGuard} so only
 *   COMPANY_ADMIN users can land there.
 *
 * <p>Prior to refactor-1, `/team` was a CHILD of `/dashboard`
 * (URL: `/dashboard/team/*`). It was a pragmatic choice to
 * reuse the dashboard shell, but it conflated two distinct
 * concepts: the dashboard home is a single page; the team
 * feature is its own surface. The refactor promoted both to
 * siblings under `auth`.
 *
 * <p>The two layouts + the auth guard are eagerly imported
 * (they're tiny); the leaf F1 components + the 4 team page
 * components are `loadComponent` lazy so the initial bundle
 * stays under the 1MB budget.
 */
export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./features/home/home').then((m) => m.HomeComponent),
  },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/login/login').then((m) => m.LoginComponent),
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/register/wizard/register').then((m) => m.RegisterComponent),
  },
  {
    path: 'terms',
    loadComponent: () =>
      import('./features/terms/terms').then((m) => m.TermsComponent),
  },
  {
    path: 'privacy',
    loadComponent: () =>
      import('./features/privacy/privacy').then((m) => m.PrivacyComponent),
  },
  {
    // etapa-2-usuarios refactor-1: the authenticated shell
    // is mounted as the `auth` parent so that /dashboard and
    // /team can be siblings instead of /team being nested
    // under /dashboard. The `authGuard` fires once at this
    // level — both children inherit the gate.
    path: 'auth',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./core/layouts/auth-layout/auth-layout').then((m) => m.AuthLayoutComponent),
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then(
            (m) => m.DashboardComponent,
          ),
      },
      {
        // etapa-2-usuarios / PR-5 — team management feature.
        // The teamAccessGuard fires once at the `/auth/team`
        // parent level (children inherit the gate), so
        // non-admins are redirected to `/auth/dashboard`
        // before any of the lazy children render.
        path: 'team',
        canActivate: [teamAccessGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/team/list/team-list').then((m) => m.TeamListComponent),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./features/team/create/team-create').then((m) => m.TeamCreateComponent),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/team/detail/team-detail').then((m) => m.TeamDetailComponent),
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('./features/team/edit/team-edit').then((m) => m.TeamEditComponent),
          },
        ],
      },
      {
        // etapa-3-envios / PR-6 — customer management feature
        // (Chunk B: wire-up). The customerAccessGuard fires
        // once at the `/auth/customers` parent level; unlike
        // teamAccessGuard it does NOT require admin — operators
        // and viewers can browse customers (they are
        // operational data, not admin config).
        path: 'customers',
        canActivate: [customerAccessGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/customers/list/customer-list').then((m) => m.CustomerListComponent),
          },
          {
            path: 'new',
            loadComponent: () =>
              import('./features/customers/create/customer-create').then((m) => m.CustomerCreateComponent),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/customers/detail/customer-detail').then((m) => m.CustomerDetailComponent),
          },
          {
            path: ':id/edit',
            loadComponent: () =>
              import('./features/customers/edit/customer-edit').then((m) => m.CustomerEditComponent),
          },
        ],
      },
      {
        // etapa-3-envios / PR-7 (Chunk A — list + detail,
        // Chunk B — wizard + edit). The shipmentAccessGuard
        // fires once at the `/auth/shipments` parent level;
        // unlike teamAccessGuard it does NOT require admin —
        // shipments are the core operational entity and
        // operators must be able to browse + manage them.
        path: 'shipments',
        canActivate: [shipmentAccessGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/shipments/list/shipment-list').then((m) => m.ShipmentListComponent),
          },
          {
            // etapa-3-envios / PR-7 Chunk B — the create
            // wizard. Lazy-loaded so the list + detail
            // bundles stay small.
            path: 'new',
            loadComponent: () =>
              import('./features/shipments/wizard/shipment-create').then((m) => m.ShipmentCreateComponent),
          },
          {
            path: ':id',
            loadComponent: () =>
              import('./features/shipments/detail/shipment-detail').then((m) => m.ShipmentDetailComponent),
          },
          {
            // etapa-3-envios / PR-7 Chunk B — the edit page.
            // Only valid when the shipment is in PRE_ALTA
            // (enforced by the backend and surfaced via an
            // inline error banner on the page when the user
            // hits it from a non-editable state).
            path: ':id/edit',
            loadComponent: () =>
              import('./features/shipments/edit/shipment-edit').then((m) => m.ShipmentEditComponent),
          },
        ],
      },
    ],
  },
  // --- Wildcard fallback ---------------------------------
  // Land inside the workspace shell so authenticated users
  // hitting a stale bookmark (e.g. `/dashboard` from before
  // refactor-1) bounce into the app instead of the public
  // home. The `authGuard` on the `auth` parent will redirect
  // unauthenticated users to `/login?returnUrl=...` with
  // `auth/dashboard` as the returnUrl, which is what we want.
  { path: '**', redirectTo: 'auth/dashboard' },
];
