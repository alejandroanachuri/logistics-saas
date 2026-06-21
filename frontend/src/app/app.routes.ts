import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth-guard';
import { teamAccessGuard } from './core/guards/team-access.guard';

/**
 * F1 + gap-#5 + etapa-2-usuarios routes. Two layouts:
 * - Public for ''/login/register/terms/privacy (eagerly rendered
 *   layout wrappers where applicable, lazy leaves for the
 *   trivial pages).
 * - Authenticated for 'dashboard' (the workspace shell) with a
 *   wildcard fallback that lands on the home page.
 *
 * <p>Since etapa-2-usuarios the dashboard layout also hosts
 * the team management feature (`/team/*`). The team routes
 * are gated by `teamAccessGuard` which redirects non-admins
 * to `/dashboard`. The guard fires once at the `/team`
 * parent level — child routes inherit the gate.
 *
 * <p>The lazy children are mounted as each page component
 * lands in its own commit:
 * - `''` (list) — PR-5 / T-5.4
 * - `'new'` (create) — T-5.5
 * - `':id'` (detail) — T-5.6
 * - `':id/edit'` (edit) — T-5.7
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
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard-shell').then(
        (m) => m.DashboardShellComponent,
      ),
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/dashboard/dashboard').then(
            (m) => m.DashboardComponent,
          ),
      },
      {
        // etapa-2-usuarios / PR-5 — team management feature.
        // The teamAccessGuard fires once at the `/team` parent
        // level (children inherit the gate), so non-admins are
        // redirected to `/dashboard` before any of the lazy
        // children render.
        path: 'team',
        canActivate: [teamAccessGuard],
        children: [
          {
            path: '',
            loadComponent: () =>
              import('./features/team/list/team-list').then((m) => m.TeamListComponent),
          },
        ],
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
