import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/**
 * Tiny public landing page for the F1 slice. The real
 * hero/feature content lands in a later iteration; v1
 * just needs the public route to render something so the
 * e2e gate has a real DOM to assert against.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="w-full max-w-md">
      <section class="rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <h1 class="text-2xl font-semibold text-slate-900">Logística SaaS</h1>
        <p class="mt-2 text-sm text-slate-600">
          Gestioná envíos, clientes y facturación para tu empresa.
        </p>
        <div class="mt-6 flex flex-col gap-3">
          <a
            routerLink="/login"
            class="rounded-md bg-slate-900 px-4 py-2 text-center text-sm font-medium text-white hover:bg-slate-800"
          >
            Ir a iniciar sesión
          </a>
          <a
            routerLink="/register"
            class="rounded-md border border-slate-300 px-4 py-2 text-center text-sm font-medium text-slate-700 hover:bg-slate-50"
          >
            Registrate
          </a>
        </div>
      </section>
    </main>
  `,
})
export class HomeComponent {}
