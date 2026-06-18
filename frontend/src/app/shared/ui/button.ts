import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Button — design-system primary interactive element.
 *
 * Implements the buttons section of /DESIGN.md. Three
 * variants (primary, secondary, ghost) plus a danger
 * variant for destructive actions. Three sizes.
 *
 * Variants:
 * - primary   — blue 600 background, white text. The main
 *   CTA on a page.
 * - secondary — white background, slate 200 border, slate 900
 *   text. For secondary actions ("Cancel", "Back").
 * - ghost     — no background, slate 700 text. For tertiary
 *   actions in dense layouts.
 * - danger    — red 600 background, white text. Destructive
 *   ("Delete", "Remove").
 *
 * Sizes:
 * - sm — h-8 px-3 text-sm
 * - md — h-10 px-4 text-sm (default)
 * - lg — h-12 px-6 text-base
 *
 * Focus state: 2px offset primary ring (per DESIGN.md
 * §Components / Buttons / Focus State).
 *
 * Disabled state: 50% opacity + cursor-not-allowed.
 *
 * Usage:
 *   <app-button variant="primary" (click)="save()">Guardar</app-button>
 *   <app-button variant="secondary" type="button">Cancelar</app-button>
 */
@Component({
  selector: 'app-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      [type]="type()"
      [disabled]="disabled() || loading()"
      [attr.aria-busy]="loading() || null"
      [class]="classes()"
    >
      @if (loading()) {
        <span aria-hidden="true" class="mr-2 inline-block h-3 w-3 animate-spin rounded-full border-2 border-current border-t-transparent"></span>
      }
      <ng-content />
    </button>
  `,
})
export class ButtonComponent {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly type = input<'button' | 'submit' | 'reset'>('button');
  readonly disabled = input<boolean>(false);
  readonly loading = input<boolean>(false);
  readonly fullWidth = input<boolean>(false);

  protected readonly classes = computed(() => {
    const v = this.variant();
    const s = this.size();
    const fw = this.fullWidth() ? 'w-full' : '';

    // Base: inline-flex items-center justify-center rounded-md
    // font-semibold transition-colors focus-visible:outline-none
    // focus-visible:ring-2 focus-visible:ring-primary
    // focus-visible:ring-offset-2 disabled:opacity-50
    // disabled:cursor-not-allowed
    const base =
      'inline-flex items-center justify-center rounded-md font-semibold transition-colors ' +
      'focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ' +
      'focus-visible:ring-offset-2 focus-visible:ring-offset-background ' +
      'disabled:opacity-50 disabled:cursor-not-allowed';

    const sizeClass =
      s === 'sm'
        ? 'h-8 px-3 text-sm'
        : s === 'lg'
          ? 'h-12 px-6 text-base'
          : 'h-10 px-4 text-sm';

    const variantClass =
      v === 'primary'
        ? 'bg-primary text-on-primary hover:bg-primary-fixed-dim active:bg-primary-fixed'
        : v === 'secondary'
          ? 'bg-surface-container-lowest text-on-surface border border-outline-variant hover:bg-surface-container-low'
          : v === 'danger'
            ? 'bg-error text-on-error hover:bg-error/90'
            : 'bg-transparent text-on-surface-variant hover:bg-surface-container-low';

    return `${base} ${sizeClass} ${variantClass} ${fw}`.trim();
  });
}
