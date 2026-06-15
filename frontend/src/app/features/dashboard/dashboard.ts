import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { AuthStore } from '../../core/state/auth-store';
import { TenantStore } from '../../core/state/tenant-store';

/**
 * Dashboard placeholder. Reads from {@code AuthStore} and
 * {@code TenantStore} only — no HTTP. The full dashboard
 * (KPIs, recent activity, etc.) is v2; F1 just renders the
 * identity confirmation copy from {@code auth-shell-layout.md}
 * §"Dashboard Placeholder Content" so the spec's
 * {@code curl} / e2e gate has a real DOM to assert against.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  template: `
    @if (authStore.currentUser(); as user) {
      <div class="space-y-4">
        <h1 class="text-2xl font-semibold text-slate-900">
          Bienvenido, {{ user.firstName }}
        </h1>
        <p class="text-sm text-slate-600">
          Tu slug es: {{ tenant()?.slug || user.tenantSlug }}
        </p>
        <p class="text-sm text-slate-600">
          Tu tenant ID es: {{ user.tenantId }}
        </p>
        <div class="pt-4">
          <button
            type="button"
            (click)="logout()"
            class="rounded-md border border-slate-300 px-3 py-1.5 text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Cerrar sesión
          </button>
        </div>
      </div>
    } @else {
      <p class="text-sm text-slate-600">Cargando…</p>
    }
  `,
})
export class DashboardComponent {
  protected readonly authStore = inject(AuthStore);
  private readonly authService = inject(AuthService);
  private readonly tenantStore = inject(TenantStore);
  private readonly router = inject(Router);

  protected tenant() {
    return this.tenantStore.currentTenant();
  }

  logout(): void {
    this.authService.logout().subscribe({
      next: () => {
        this.router.navigate(['/']);
      },
      error: () => {
        this.authStore.clear();
        this.tenantStore.clear();
        this.router.navigate(['/']);
      },
    });
  }
}
