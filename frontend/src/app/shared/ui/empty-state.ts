import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

import { ButtonComponent } from './button';

/**
 * EmptyState — centered empty-state block with optional CTA.
 *
 * <p>Used by team-list in PR-5 when the result set is empty,
 * and by the future shipment/customer pages when they ship.
 *
 * <p>Renders an icon (emoji or text glyph) at 48px + a title
 * + a descriptive message + an optional CTA button. The CTA
 * emits `ctaClick`; the parent handles navigation or modal
 * opening.
 *
 * Standalone, OnPush, signal-input / output API.
 */
@Component({
  selector: 'app-empty-state',
  standalone: true,
  imports: [ButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex flex-col items-center justify-center gap-3 py-12 text-center">
      <span aria-hidden="true" class="text-5xl">{{ icon() }}</span>
      <h3 class="text-lg font-semibold text-on-surface">{{ title() }}</h3>
      <p class="max-w-md text-sm text-on-surface-variant">{{ message() }}</p>
      @if (ctaLabel(); as label) {
        <app-button variant="primary" data-cta (click)="ctaClick.emit()">
          {{ label }}
        </app-button>
      }
    </div>
  `,
})
export class EmptyStateComponent {
  readonly icon = input.required<string>();
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly ctaLabel = input<string | null>(null);

  readonly ctaClick = output<void>();
}
