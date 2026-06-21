import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { ButtonComponent } from './button';

/**
 * ConfirmDialog — generic confirmation modal for destructive
 * or non-destructive actions. Used by:
 * - team-detail: confirm disable / reactivate (destructive)
 * - team-edit:   confirm cancel-with-unsaved-changes
 *
 * Behavior:
 * - Click the confirm button -> emits {@code confirm}.
 * - Click the cancel button  -> emits {@code cancel}.
 * - Click the overlay backdrop (outside the panel) -> emits
 *   {@code cancel} (standard modal dismiss semantics).
 *
 * The component does NOT manage a visibility state. The
 * parent renders it conditionally (`@if (showConfirm())`) so
 * the modal disappears on the next change-detection cycle
 * after the user clicks either button.
 *
 * Standalone, OnPush, signal-input / output API.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [ButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      data-overlay
      role="dialog"
      aria-modal="true"
      [attr.aria-labelledby]="titleId()"
      (click)="onBackdropClick($event)"
      class="fixed inset-0 z-50 flex items-center justify-center bg-on-surface/60 p-4"
    >
      <div
        data-panel
        (click)="$event.stopPropagation()"
        class="w-full max-w-sm rounded-md border border-outline-variant bg-surface-container-lowest p-6 elev-2"
      >
        <h2 [id]="titleId()" class="text-lg font-semibold text-on-surface">
          {{ title() }}
        </h2>
        <p class="mt-2 text-sm text-on-surface-variant">{{ message() }}</p>

        <div class="mt-6 flex justify-end gap-2">
          <app-button variant="ghost" data-cancel (click)="cancel.emit()">
            {{ cancelLabel() }}
          </app-button>
          <app-button
            [variant]="confirmVariant()"
            data-confirm
            (click)="confirm.emit()"
          >
            {{ confirmLabel() }}
          </app-button>
        </div>
      </div>
    </div>
  `,
})
export class ConfirmDialogComponent {
  readonly title = input.required<string>();
  readonly message = input.required<string>();
  readonly confirmLabel = input.required<string>();
  readonly cancelLabel = input.required<string>();
  /** When true, the confirm button renders with the error
   * palette (destructive action). Default false (primary). */
  readonly destructive = input<boolean>(false);

  readonly confirm = output<void>();
  readonly cancel = output<void>();

  protected readonly confirmVariant = computed<'primary' | 'danger'>(() =>
    this.destructive() ? 'danger' : 'primary',
  );

  /** Stable id for the title (so `aria-labelledby` resolves). */
  protected readonly titleId = computed(() => `confirm-title-${titleKey(this.title())}`);

  protected onBackdropClick(event: MouseEvent): void {
    // Only fire when the click landed directly on the overlay,
    // not bubbled up from the inner panel (which stops
    // propagation above).
    if (event.target === event.currentTarget) {
      this.cancel.emit();
    }
  }
}

function titleKey(title: string): string {
  return title.replace(/[^a-zA-Z0-9]/g, '-').toLowerCase();
}
