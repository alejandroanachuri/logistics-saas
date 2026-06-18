import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

import { CardComponent } from '../../shared/ui/card';

/**
 * Public landing page for the F1 slice. The full hero /
 * feature content (per /DESIGN.md) lands in a later
 * iteration; v1 just needs the public route to render
 * something with the design system applied so the
 * visual surface is consistent with the rest of the app.
 */
@Component({
  selector: 'app-home',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CardComponent],
  template: `
    <main class="mx-auto w-full max-w-md px-4 py-16">
      <app-card>
        <div class="text-center">
          <h1 class="text-3xl font-semibold text-on-surface">Logística SaaS</h1>
          <p class="mt-3 text-base text-on-surface-variant">
            Gestioná envíos, clientes y facturación para tu empresa.
          </p>
        </div>
        <div class="mt-8 flex flex-col gap-3">
          <a
            routerLink="/login"
            class="inline-flex h-10 w-full items-center justify-center rounded-md bg-primary px-4 text-sm font-semibold text-on-primary hover:bg-primary-fixed-dim focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background"
          >
            Ir a iniciar sesión
          </a>
          <a
            routerLink="/register"
            class="inline-flex h-10 w-full items-center justify-center rounded-md border border-outline-variant bg-surface-container-lowest px-4 text-sm font-semibold text-on-surface hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 focus-visible:ring-offset-background"
          >
            Registrate
          </a>
        </div>
      </app-card>
    </main>
  `,
})
export class HomeComponent {}

