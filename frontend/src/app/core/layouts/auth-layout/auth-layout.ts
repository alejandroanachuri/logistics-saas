import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith } from 'rxjs/operators';
import { toSignal } from '@angular/core/rxjs-interop';

import { AuthService } from '../../../core/services/auth.service';
import { AuthStore } from '../../../core/state/auth-store';
import { TenantStore } from '../../../core/state/tenant-store';
import { ButtonComponent } from '../../../shared/ui/button';

interface NavItem {
  readonly label: string;
  readonly icon: string; // Unicode / emoji for v1; SVG icons in F2+
  readonly route: string;
  readonly disabled?: boolean;
  readonly disabledHint?: string;
  /**
   * Optional visibility gate. When set, the item only renders
   * if `visible()` returns true. Used for the role-gated
   * "Equipo" item added in etapa-2-usuarios PR-5 — the gate
   * reads `AuthStore.currentUserIsAdmin()` so non-admins
   * never see the link.
   */
  readonly visible?: () => boolean;
}

/**
 * Map a current route URL to the header title. Centralized so
 * the 4 team pages + the dashboard home + any future feature
 * pages share a single source of truth.
 *
 * <p>Since refactor-1 the canonical paths live under `/auth/`
 * (`/auth/dashboard`, `/auth/team/*`). The function still
 * recognises the legacy `/team/*` and `/dashboard` paths so
 * that mid-navigation events (or stale URLs that haven't
 * hit the top-level redirect yet) keep the title coherent.
 *
 * Longest paths win (`/auth/team/new` matches before
 * `/auth/team`), so the prefix-first scan below resolves
 * correctly.
 */
function titleForUrl(url: string): string {
  // Strip query string + hash + trailing slash for matching.
  const path = url.split('?')[0].split('#')[0].replace(/\/+$/, '') || '/';
  if (path === '/auth/team/new' || path.startsWith('/auth/team/new/')) return 'Nuevo usuario';
  if (path.match(/^\/auth\/team\/[^/]+\/edit\/?$/)) return 'Editar usuario';
  if (path.match(/^\/auth\/team\/[^/]+\/?$/)) return 'Detalle del usuario';
  if (path === '/auth/team' || path.startsWith('/auth/team')) return 'Equipo';
  if (path === '/auth/dashboard') return 'Dashboard';
  // Legacy paths (pre-refactor-1 routes still recognised so
  // title updates correctly during the NavigationEnd that
  // triggers the redirect).
  if (path === '/team/new' || path.startsWith('/team/new/')) return 'Nuevo usuario';
  if (path.match(/^\/team\/[^/]+\/edit\/?$/)) return 'Editar usuario';
  if (path.match(/^\/team\/[^/]+\/?$/)) return 'Detalle del usuario';
  if (path === '/team' || path.startsWith('/team')) return 'Equipo';
  if (path === '/dashboard' || path === '/dashboard/') return 'Dashboard';
  return 'Dashboard';
}

/**
 * Auth layout — the authenticated workspace shell (previously
 * named `DashboardShellComponent`).
 *
 * <p>This component is mounted as the layout for the `auth`
 * parent route in `app.routes.ts`. It wraps both the
 * dashboard home (`/auth/dashboard`) and the team management
 * feature (`/auth/team/*`). The class was renamed from
 * `DashboardShellComponent` in etapa-2-usuarios refactor-1
 * because the shell is no longer "just the dashboard" — it
 * hosts any authenticated feature page.
 *
 * Renders a 3-region layout per /DESIGN.md:
 * - Left sidebar (240px on desktop, 0px on mobile — for v1 we
 *   don't implement a mobile drawer; the app is
 *   desktop-first per the logistics B2B use case).
 * - Top header (full-width inside the main area).
 * - Main content area: <router-outlet /> for feature
 *   pages.
 * - Bottom footer (version + legal links).
 *
 * The sidebar nav lists the F1-ready routes plus placeholders
 * for upcoming features (Envíos, Clientes, Reportes,
 * Configuración). The placeholders are rendered as
 * disabled buttons with a 'Próximamente' tooltip so the
 * user can see what is coming without expecting it to work.
 *
 * <p>Since etapa-2-usuarios PR-5 the sidebar also exposes an
 * "Equipo" item, gated by
 * `AuthStore.currentUserIsAdmin()` — only COMPANY_ADMIN
 * users see the link. Non-admins are also blocked at the
 * route level by `teamAccessGuard` (defense in depth).
 *
 * <p>When the sidebar item is the current route, the item
 * is highlighted via the {@code routerLinkActive} directive
 * with a primary background.
 *
 * <p>On mount, if the store reports authenticated but no
 * {@code currentUser} is cached (cold-boot), the shell
 * calls {@code AuthService.me()} to rehydrate. If that
 * fails, the errorInterceptor's forced-logout branch
 * redirects to {@code /login}.
 */
@Component({
  selector: 'app-auth-layout',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonComponent],
  templateUrl: './auth-layout.html',
})
export class AuthLayoutComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly tenantStore = inject(TenantStore);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * Reactive signal of the current router URL, kept in sync
   * with NavigationEnd events. Drives `pageTitle()` so the
   * header reflects the active feature page (Dashboard,
   * Equipo, Nuevo usuario, etc.).
   */
  private readonly currentUrl = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /**
   * Sidebar nav items. The first entry is the active
   * dashboard home; the rest are upcoming features marked
   * as disabled in v1, plus the role-gated "Equipo" item
   * (PR-5) which only renders when
   * `AuthStore.currentUserIsAdmin()` is true.
   */
  protected readonly navItems = computed<readonly NavItem[]>(() => {
    const baseItems: NavItem[] = [
      // refactor-1: Dashboard lives at /auth/dashboard (was
      // /dashboard). The Equipo item below uses absolute
      // `/team` which the top-level redirect in
      // app.routes.ts forwards to `/auth/team` so the user
      // lands inside the shell without a flash.
      { label: 'Dashboard', icon: '◉', route: '/auth/dashboard' },
      { label: 'Envíos', icon: '➤', route: '/shipments', disabled: true, disabledHint: 'Próximamente' },
      // etapa-3-envios / PR-6 Chunk B: the Clientes item is
      // enabled here. AuthStore has no `currentUserIsOperator`
      // computed yet, so the gate uses the same local-fallback
      // pattern as Chunk A's per-page gate: ADMIN OR OPERATOR
      // roles can see the link. VIEWER/DRIVER do not — they
      // don't create or edit customers.
      {
        label: 'Clientes',
        icon: '◐',
        route: '/auth/customers',
        visible: () =>
          this.authStore.currentUserIsAdmin() ||
          this.authStore.currentUserRoles().includes('COMPANY_OPERATOR'),
      },
      { label: 'Reportes', icon: '◈', route: '/reports', disabled: true, disabledHint: 'Próximamente' },
      { label: 'Configuración', icon: '⚙', route: '/settings', disabled: true, disabledHint: 'Próximamente' },
    ];
    // Insert the Equipo item right after Dashboard when the
    // user has COMPANY_ADMIN — it's the most-used admin
    // surface, so it deserves prominence over the
    // disabled placeholders.
    if (this.authStore.currentUserIsAdmin()) {
      baseItems.splice(1, 0, {
        label: 'Equipo',
        icon: '👥',
        route: '/auth/team',
      });
    }
    return baseItems;
  });

  /**
   * Header title driven by the current route. Reads the
   * `currentUrl` signal and maps it via `titleForUrl`.
   */
  protected readonly pageTitle = computed(() => titleForUrl(this.currentUrl()));

  /**
   * Brand + version for the footer.
   */
  protected readonly appVersion = '0.1.0';
  protected readonly currentYear = new Date().getFullYear();

  constructor() {
    if (this.authStore.isAuthenticated() && !this.authStore.currentUser()) {
      this.authService.me().subscribe({
        error: () => {
          // The errorInterceptor has already cleared the
          // stores and navigated to /login. Nothing more
          // to do.
        },
      });
    }
  }

  protected onNavClick(item: NavItem, event: MouseEvent): void {
    if (item.disabled) {
      event.preventDefault();
    }
  }

  protected logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        void this.router.navigate(['/']);
      },
      error: () => {
        // Even if the backend logout fails (e.g. the
        // refresh cookie was already cleared), we still
        // wipe local state.
        this.authStore.clear();
        this.tenantStore.clear();
        void this.router.navigate(['/']);
      },
    });
  }
}
