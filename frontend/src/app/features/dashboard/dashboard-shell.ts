import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AuthStore } from '../../core/state/auth-store';
import { TenantStore } from '../../core/state/tenant-store';
import { ButtonComponent } from '../../shared/ui/button';

interface NavItem {
  readonly label: string;
  readonly icon: string; // Unicode / emoji for v1; SVG icons in F2+
  readonly route: string;
  readonly disabled?: boolean;
  readonly disabledHint?: string;
}

/**
 * Dashboard shell — the authenticated workspace layout.
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
  selector: 'app-dashboard-shell',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, ButtonComponent],
  templateUrl: './dashboard-shell.html',
})
export class DashboardShellComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly tenantStore = inject(TenantStore);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /**
   * Sidebar nav items. The first entry is the active
   * dashboard home; the rest are upcoming features marked
   * as disabled in v1. The 'route' field is what the
   * routerLink would point to; disabled items ignore the
   * click and surface a 'Próximamente' tooltip instead.
   */
  protected readonly navItems = signal<readonly NavItem[]>([
    { label: 'Dashboard', icon: '◉', route: '/dashboard' },
    { label: 'Envíos', icon: '➤', route: '/shipments', disabled: true, disabledHint: 'Próximamente' },
    { label: 'Clientes', icon: '◐', route: '/customers', disabled: true, disabledHint: 'Próximamente' },
    { label: 'Reportes', icon: '◈', route: '/reports', disabled: true, disabledHint: 'Próximamente' },
    { label: 'Configuración', icon: '⚙', route: '/settings', disabled: true, disabledHint: 'Próximamente' },
  ]);

  /**
   * Header title driven by the current route. For v1 the
   * dashboard is the only authenticated route, so the title
   * is hard-coded to 'Dashboard'. When more feature pages
   * land, this becomes a route-data-driven lookup.
   */
  protected readonly pageTitle = computed(() => 'Dashboard');

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
