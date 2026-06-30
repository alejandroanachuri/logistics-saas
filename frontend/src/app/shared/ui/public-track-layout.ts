import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * PublicTrackLayout — minimal layout for the public tracking page.
 *
 * <p>Renders a brand header (LogiStart wordmark), a content slot
 * via {@code <router-outlet />}, and a minimal footer with copyright
 * info. There is intentionally NO top navigation and NO user
 * menu: this layout serves the unauthenticated tracking endpoint
 * where customers paste a {@code LGST-XXXXXXXX} code and read the
 * shipment status without ever touching the operator shell.
 *
 * <p>Visual: brand header uses the primary container (on-primary)
 * token so the page reads as a marketing-style surface rather
 * than the operator shell's surface-container-low. The body uses
 * the default background; the footer uses the surface-variant
 * divider token and the on-surface-variant text token.
 *
 * <p>This is the v2 layout for the public-tracking route —
 * {@code /track/:lgstId}. The existing
 * {@code PublicLayoutComponent} (used by {@code /login} and
 * {@code /register}) keeps its v1 layout intentionally because
 * those pages need the auth shell branding.
 *
 * <p>Standalone, OnPush, no DI dependencies.
 */
@Component({
  selector: 'app-public-track-layout',
  standalone: true,
  imports: [RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex min-h-screen flex-col bg-background text-on-surface">
      <header class="bg-primary py-4 text-on-primary" data-public-track-header>
        <div class="mx-auto max-w-4xl px-4">
          <h1 class="text-xl font-semibold">LogiStart</h1>
        </div>
      </header>
      <main class="mx-auto w-full max-w-4xl flex-1 px-4 py-8" data-public-track-main>
        <router-outlet />
      </main>
      <footer
        class="mt-12 border-t border-outline-variant py-4"
        data-public-track-footer
      >
        <div
          class="mx-auto max-w-4xl px-4 text-sm text-on-surface-variant"
        >
          Rastreo de envíos · LogiStart © 2026
        </div>
      </footer>
    </div>
  `,
})
export class PublicTrackLayoutComponent {}
