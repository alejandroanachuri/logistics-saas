import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type UserStatus = 'ACTIVE' | 'DISABLED';

/**
 * Map a user {@code UserStatus} to its Spanish label and
 * the design-system tokens the badge should render with.
 */
function statusCopy(status: UserStatus): { label: string; dotClass: string; containerClass: string } {
  switch (status) {
    case 'ACTIVE':
      return {
        label: 'Activo',
        dotClass: 'bg-tertiary',
        containerClass: 'bg-tertiary-container text-on-tertiary-container',
      };
    case 'DISABLED':
      return {
        label: 'Deshabilitado',
        dotClass: 'bg-error',
        containerClass: 'bg-error-container text-on-error-container',
      };
  }
}

/**
 * StatusBadge — visual pill indicating whether a user is
 * active or disabled. Used by team-list / team-detail in
 * PR-5 and by the dashboard account info card when admin
 * accounts are surfaced.
 *
 * Renders a small colored dot + Spanish label inside a
 * pill. Colors come from existing design-system tokens
 * (tertiary-container for ACTIVE = positive, error-container
 * for DISABLED = negative).
 *
 * Standalone, OnPush, signal-input API.
 */
@Component({
  selector: 'app-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      [attr.data-status]="status()"
      [class]="containerClass()"
      [attr.aria-label]="ariaLabel()"
    >
      <span
        data-status-dot
        aria-hidden="true"
        [class]="dotClass() + ' inline-block h-2 w-2 rounded-full'"
      ></span>
      {{ label() }}
    </span>
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<UserStatus>();

  protected readonly copy = computed(() => statusCopy(this.status()));
  protected readonly label = computed(() => this.copy().label);
  protected readonly dotClass = computed(() => this.copy().dotClass);
  protected readonly containerClass = computed(
    () =>
      this.copy().containerClass +
      ' inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-semibold',
  );
  protected readonly ariaLabel = computed<string>(
    () => `Estado: ${this.label()}`,
  );
}
