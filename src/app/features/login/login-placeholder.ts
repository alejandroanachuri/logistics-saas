import { Component } from '@angular/core';

/**
 * Placeholder for the F1 login page. The real
 * {@code LoginComponent} lands in PR11; this component
 * keeps the {@code /login} route reachable in PR10 so the
 * auth shell end-to-end story (public layout -> login link
 * -> login page renders -> authGuard redirect) compiles and
 * navigates correctly.
 */
@Component({
  selector: 'app-login-placeholder',
  standalone: true,
  template: `
    <main class="w-full max-w-md">
      <section class="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 class="text-2xl font-semibold text-slate-900">Iniciar sesión</h1>
        <p class="mt-2 text-sm text-slate-600">Login coming in PR11</p>
      </section>
    </main>
  `,
})
export class LoginPlaceholderComponent {}
