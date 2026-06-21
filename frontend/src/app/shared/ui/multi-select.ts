import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { Role } from '../../core/types';

/**
 * MultiSelect — checkbox group for picking 1+ roles from a
 * small catalog. Built with pure HTML checkboxes (not the
 * CDK listbox, which targets single-select).
 *
 * <p>Used by team-create and team-edit in PR-5 to assign
 * roles to a user. The component is purely presentational:
 * it owns no signal of the selection (the parent's
 * `companyUsersStore` does). The parent passes the current
 * `selected` ids and listens to `selectedChange` to commit
 * the diff.
 *
 * <p>Two protection mechanisms:
 * - {@code disabledIds}: specific role ids render as disabled
 *   checkboxes (e.g. the first admin's COMPANY_ADMIN role
 *   when it would leave the tenant without an admin).
 * - {@code adminRoleIds}: when any selected id is an admin
 *   role, a warning banner surfaces explaining the access
 *   implications.
 *
 * Standalone, OnPush, signal-input / output API.
 */
@Component({
  selector: 'app-multi-select',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-3">
      @if (showAdminWarning()) {
        <div
          data-admin-warning
          role="status"
          class="rounded-md border border-warning bg-warning-container px-3 py-2 text-sm text-on-warning-container"
        >
          Los administradores tienen acceso total a la empresa, incluida la gestión de usuarios.
        </div>
      }
      <fieldset class="space-y-2">
        <legend class="sr-only">Roles</legend>
        @for (option of options(); track option.id) {
          <label
            [class]="rowClasses(option.id)"
            class="flex items-start gap-3 rounded-md border border-outline-variant bg-surface-container-lowest p-3"
          >
            <input
              type="checkbox"
              [checked]="isSelected(option.id)"
              [disabled]="isDisabled(option.id)"
              [attr.aria-describedby]="descriptionId(option.id)"
              (change)="onToggle(option.id, $event)"
              class="mt-0.5 h-4 w-4 rounded border-outline-variant text-primary focus:ring-primary"
            />
            <span class="flex-1">
              <span class="block text-sm font-semibold text-on-surface">{{ option.name }}</span>
              @if (option.description; as desc) {
                <span [id]="descriptionId(option.id)" class="block text-xs text-on-surface-variant">
                  {{ desc }}
                </span>
              }
            </span>
          </label>
        }
      </fieldset>
    </div>
  `,
})
export class MultiSelectComponent {
  readonly options = input.required<Role[]>();
  /** Ids of options currently selected. */
  readonly selected = input.required<string[]>();
  /** Ids of options that should render as disabled checkboxes. */
  readonly disabledIds = input<string[]>([]);
  /** Ids of role(s) considered "admin" — surfaces the warning
   * banner when any of them is in `selected`. */
  readonly adminRoleIds = input<string[]>([]);

  readonly selectedChange = output<string[]>();

  private readonly selectedSet = computed(() => new Set(this.selected()));
  private readonly disabledSet = computed(() => new Set(this.disabledIds()));

  protected isSelected(id: string): boolean {
    return this.selectedSet().has(id);
  }

  protected isDisabled(id: string): boolean {
    return this.disabledSet().has(id);
  }

  protected descriptionId(id: string): string {
    return `multi-select-desc-${id}`;
  }

  protected rowClasses(_id: string): string {
    return '';
  }

  protected readonly showAdminWarning = computed(() => {
    const selected = this.selectedSet();
    return this.adminRoleIds().some((id) => selected.has(id));
  });

  protected onToggle(id: string, event: Event): void {
    if (this.isDisabled(id)) return;
    const checkbox = event.target as HTMLInputElement;
    const current = this.selected();
    const next = checkbox.checked
      ? [...current, id]
      : current.filter((x) => x !== id);
    this.selectedChange.emit(next);
  }
}
