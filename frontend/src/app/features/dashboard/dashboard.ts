import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { AuthStore } from '../../core/state/auth-store';
import { TenantStore } from '../../core/state/tenant-store';
import { CardComponent } from '../../shared/ui/card';

/**
 * Dashboard home — the default authenticated page rendered
 * inside the {@code <router-outlet />} of the
 * {@code AuthLayoutComponent}. Reads from
 * {@code AuthStore} and {@code TenantStore} only — no
 * HTTP. The KPIs (Envíos hoy, Clientes activos, etc.) and
 * the recent-activity feed are v2; this v1 placeholder
 * renders the identity confirmation + a 3-card KPI
 * scaffold so the user can see the design system in
 * action immediately after login.
 */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CardComponent],
  template: `
    <div class="space-y-6">
      @if (authStore.currentUser(); as user) {
        <header>
          <h2 class="text-2xl font-semibold text-on-surface">Bienvenido, {{ user.firstName }}</h2>
          @if (tenantStore.currentTenant(); as t) {
            <p class="mt-1 text-sm text-on-surface-variant">
              {{ t.legalName }} · <span class="tabular-nums">{{ t.slug }}</span>
            </p>
          } @else {
            <p class="mt-1 text-sm text-on-surface-variant">Cargando empresa…</p>
          }
        </header>

        <section class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          <app-card>
            <p class="text-sm font-medium text-on-surface-variant">Envíos hoy</p>
            <p class="mt-2 text-3xl font-semibold tabular-nums text-on-surface">—</p>
            <p class="mt-1 text-xs text-on-surface-variant">Próximamente</p>
          </app-card>
          <app-card>
            <p class="text-sm font-medium text-on-surface-variant">Clientes activos</p>
            <p class="mt-2 text-3xl font-semibold tabular-nums text-on-surface">—</p>
            <p class="mt-1 text-xs text-on-surface-variant">Próximamente</p>
          </app-card>
          <app-card>
            <p class="text-sm font-medium text-on-surface-variant">Entregas pendientes</p>
            <p class="mt-2 text-3xl font-semibold tabular-nums text-on-surface">—</p>
            <p class="mt-1 text-xs text-on-surface-variant">Próximamente</p>
          </app-card>
        </section>

        <app-card>
          <div card-header>Información de la cuenta</div>
          <dl class="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <div>
              <dt class="text-xs uppercase tracking-wide text-on-surface-variant">Tenant ID</dt>
              <dd class="mt-1 break-all text-sm tabular-nums text-on-surface">{{ user.tenantId }}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wide text-on-surface-variant">Usuario</dt>
              <dd class="mt-1 text-sm text-on-surface">{{ user.username }} · {{ user.email }}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wide text-on-surface-variant">Rol</dt>
              <dd class="mt-1 text-sm text-on-surface">{{ user.role }}</dd>
            </div>
            <div>
              <dt class="text-xs uppercase tracking-wide text-on-surface-variant">Email verificado</dt>
              <dd class="mt-1 text-sm text-on-surface">{{ user.emailVerified ? 'Sí' : 'No' }}</dd>
            </div>
          </dl>
        </app-card>
      } @else {
        <p class="text-sm text-on-surface-variant">Cargando…</p>
      }
    </div>
  `,
})
export class DashboardComponent {
  protected readonly authStore = inject(AuthStore);
  protected readonly tenantStore = inject(TenantStore);
}
