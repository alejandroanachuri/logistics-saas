import { Routes } from '@angular/router';

import { authGuard } from './core/guards/auth-guard';

/**
 * F1 routes — 4 paths in two layouts (public for ''/login/register,
 * authenticated for 'dashboard') with a wildcard fallback.
 *
 * <p>The two layouts and the two F1 placeholders are eagerly
 * imported (they're tiny); the leaf F1 components
 * ({@code HomeComponent}, {@code DashboardComponent}) are
 * {@code loadComponent} lazy so the initial bundle stays under
 * the 1MB budget.
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
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/dashboard/dashboard').then((m) => m.DashboardComponent),
  },
  { path: '**', redirectTo: '' },
];
