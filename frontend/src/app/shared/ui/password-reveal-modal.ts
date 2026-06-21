import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  output,
  signal,
} from '@angular/core';

import { ButtonComponent } from './button';

/**
 * PasswordRevealModal — full-screen modal that shows a
 * generated temporary password ONCE.
 *
 * <p>The component is rendered after `POST /company-users`
 * and `POST /company-users/{id}/reset-password` succeed. The
 * backend returns the plaintext password in the response
 * envelope; the parent component passes it as an input here.
 *
 * <p>The password is intentionally NOT stored in any signal
 * that persists past dismiss — the parent should clear its
 * own copy once `dismiss` or `createAnother` fires. This
 * component itself does not retain the value (the input is
 * just an `@Input`, not a stored signal).
 *
 * <p>Three actions:
 * - **Copiar al portapapeles** — calls
 *   `navigator.clipboard.writeText(password)` and swaps the
 *   button label to "¡Copiado!" for 2s.
 * - **Listo** — emits `dismiss` (parent navigates away).
 * - **Crear otro** — emits `createAnother` (parent resets
 *   the form and re-opens the modal on next submit).
 *
 * <p>Standalone, OnPush, signal-input / output API.
 */
@Component({
  selector: 'app-password-reveal-modal',
  standalone: true,
  imports: [ButtonComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      data-overlay
      role="dialog"
      aria-modal="true"
      aria-labelledby="password-reveal-title"
      class="fixed inset-0 z-50 flex items-center justify-center bg-on-surface/60 p-4"
    >
      <div class="w-full max-w-md rounded-md border border-outline-variant bg-surface-container-lowest p-6 elev-2">
        <h2 id="password-reveal-title" class="text-lg font-semibold text-on-surface">
          Contraseña temporal generada
        </h2>
        <p class="mt-2 text-sm text-on-surface-variant">
          Para el usuario <strong>{{ username() }}</strong>. Esta contraseña se muestra una sola vez — copiala ahora y
          compartila por un canal seguro.
        </p>

        <div
          data-password
          class="mt-4 select-all break-all rounded-md border border-outline-variant bg-surface-container-low px-4 py-3 font-mono text-base text-on-surface"
        >
          {{ temporaryPassword() }}
        </div>

        <p class="mt-3 text-sm text-warning" role="status">
          {{ warning() }}
        </p>

        <div class="mt-6 flex flex-wrap gap-2">
          <app-button
            variant="secondary"
            data-copy
            (click)="onCopy()"
          >
            {{ copyLabel() }}
          </app-button>
          <div class="ml-auto flex gap-2">
            @if (showCreateAnother()) {
              <app-button
                variant="ghost"
                data-create-another
                (click)="createAnother.emit()"
              >
                Crear otro
              </app-button>
            }
            <app-button
              variant="primary"
              data-dismiss
              (click)="dismiss.emit()"
            >
              Listo
            </app-button>
          </div>
        </div>
      </div>
    </div>
  `,
})
export class PasswordRevealModalComponent {
  readonly username = input.required<string>();
  readonly temporaryPassword = input.required<string>();
  readonly warning = input.required<string>();
  /** When true, surfaces the "Crear otro" action (used in the
   * create flow). Hidden in the reset-password flow. */
  readonly showCreateAnother = input<boolean>(false);

  readonly dismiss = output<void>();
  readonly createAnother = output<void>();

  /** Tracks whether the copy button has been pressed. */
  private readonly justCopied = signal<boolean>(false);

  protected readonly copyLabel = computed(() =>
    this.justCopied() ? '¡Copiado!' : 'Copiar al portapapeles',
  );

  protected onCopy(): void {
    const text = this.temporaryPassword();
    if (typeof navigator !== 'undefined' && navigator.clipboard) {
      void navigator.clipboard.writeText(text);
    }
    this.justCopied.set(true);
    // Reset the label back after 2 seconds.
    if (typeof setTimeout !== 'undefined') {
      setTimeout(() => this.justCopied.set(false), 2000);
    }
  }
}
