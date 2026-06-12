import { Component, inject, OnInit } from '@angular/core';
import { Router, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AuthStore } from '../../core/state/auth-store';
import { TenantStore } from '../../core/state/tenant-store';

/**
 * Authenticated layout — used as the parent route component
 * for {@code /dashboard}. Renders a top nav with the brand,
 * a user menu showing the logged-in user's firstName, a
 * {@code Cerrar sesión} button, and the routed child page
 * via {@code <router-outlet />}.
 *
 * <p>On mount, if the store reports authenticated but no
 * {@code currentUser} is cached (e.g. cold-boot from the
 * browser), the layout calls {@code AuthService.me()} to
 * rehydrate. If that fails, the errorInterceptor's
 * forced-logout branch will redirect the user to
 * {@code /login}.
 */
@Component({
  selector: 'app-authenticated-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="min-h-screen flex flex-col bg-slate-50">
      <nav class="border-b border-slate-200 bg-white">
        <div class="max-w-6xl mx-auto px-6 py-3 flex items-center justify-between">
          <span class="text-lg font-semibold text-slate-900">Logística SaaS</span>
          @if (authStore.currentUser(); as user) {
            <div class="flex items-center gap-3">
              <span class="text-sm text-slate-700">{{ user.firstName }}</span>
              <button
                type="button"
                (click)="logout()"
                class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-100"
              >
                Cerrar sesión
              </button>
            </div>
          }
        </div>
      </nav>
      <main class="flex-1 px-6 py-10">
        <div class="max-w-6xl mx-auto">
          <router-outlet />
        </div>
      </main>
    </div>
  `,
})
export class AuthenticatedLayoutComponent implements OnInit {
  protected readonly authStore = inject(AuthStore);
  private readonly authService = inject(AuthService);
  private readonly tenantStore = inject(TenantStore);
  private readonly router = inject(Router);

  ngOnInit(): void {
    if (this.authStore.isAuthenticated() && !this.authStore.currentUser()) {
      this.authService.me().subscribe({
        error: () => {
          // The errorInterceptor has already cleared the stores
          // and navigated to /login. Nothing more to do.
        },
      });
    }
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: () => {
        // Even if the backend logout fails (e.g. the refresh
        // cookie was already cleared), we still wipe the local
        // state — the user clicked logout.
        this.authStore.clear();
        this.tenantStore.clear();
        this.router.navigate(['/']);
      },
    });
  }
}
