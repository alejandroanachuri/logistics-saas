import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { Role } from '../../core/types';

export type RoleChipSize = 'sm' | 'md';

/**
 * Map a {@code Role.name} to the design-system tokens the
 * chip should render with. Each value is a pair of class
 * strings — background + on-color — pulled from /styles.css.
 * Unknown role names fall back to the neutral viewer palette
 * so a future role addition does not break the UI.
 */
function paletteForRole(name: Role['name']): {
  bg: string;
  fg: string;
} {
  switch (name) {
    case 'COMPANY_ADMIN':
      return { bg: 'bg-primary', fg: 'text-on-primary' };
    case 'COMPANY_OPERATOR':
      return { bg: 'bg-tertiary', fg: 'text-on-tertiary' };
    case 'COMPANY_DRIVER':
      return { bg: 'bg-secondary', fg: 'text-on-secondary' };
    case 'COMPANY_VIEWER':
      return { bg: 'bg-surface-container-high', fg: 'text-on-surface-variant' };
    default:
      return { bg: 'bg-surface-container-high', fg: 'text-on-surface-variant' };
  }
}

/**
 * RoleChip — visual badge for a single company role.
 *
 * Renders a compact pill with the role name and a
 * background color keyed off the role:
 * - COMPANY_ADMIN: primary container (strongest visual weight)
 * - COMPANY_OPERATOR: tertiary container (positive / actionable)
 * - COMPANY_DRIVER: secondary container (informational)
 * - COMPANY_VIEWER: neutral surface (read-only)
 *
 * Used by the team-list / team-detail pages in PR-5 to
 * surface every role a user holds. Standalone, OnPush, no
 * DI dependencies — drop into any template that passes a
 * {@code Role} via signal input.
 *
 * Usage:
 *   <app-role-chip [role]="user.roles[0]" />
 *   <app-role-chip [role]="r" size="sm" />
 */
@Component({
  selector: 'app-role-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span
      [attr.data-role]="role().name"
      [class]="classes()"
      [attr.aria-label]="ariaLabel()"
    >
      {{ role().name }}
    </span>
  `,
})
export class RoleChipComponent {
  readonly role = input.required<Role>();
  readonly size = input<RoleChipSize>('md');

  protected readonly palette = computed(() => paletteForRole(this.role().name));

  protected readonly classes = computed(() => {
    const palette = this.palette();
    const sizeClass =
      this.size() === 'sm'
        ? 'px-2 py-0.5 text-xs'
        : 'px-2.5 py-1 text-sm';
    return [
      'inline-flex items-center rounded-full font-semibold uppercase tracking-wide',
      palette.bg,
      palette.fg,
      sizeClass,
    ].join(' ');
  });

  protected readonly ariaLabel = computed<string>(
    () => `Rol ${this.role().name}`,
  );
}
