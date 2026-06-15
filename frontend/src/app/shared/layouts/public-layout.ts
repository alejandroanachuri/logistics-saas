import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * Public layout — used as the parent route component for
 * {@code ''}, {@code register}, and {@code login}. Renders a
 * brand header (placeholder copy is fine in v1) and the
 * routed child page via {@code <router-outlet />}. No top
 * navigation, no user menu — see
 * {@code auth-shell-layout.md} §"Public Layout".
 */
@Component({
  selector: 'app-public-layout',
  standalone: true,
  imports: [RouterOutlet],
  template: `
    <div class="min-h-screen flex flex-col bg-slate-50">
      <header class="border-b border-slate-200 bg-white">
        <div class="max-w-6xl mx-auto px-6 py-4">
          <span class="text-lg font-semibold text-slate-900">Logística SaaS</span>
        </div>
      </header>
      <main class="flex-1 flex items-start justify-center px-6 py-10">
        <router-outlet />
      </main>
    </div>
  `,
})
export class PublicLayoutComponent {}
