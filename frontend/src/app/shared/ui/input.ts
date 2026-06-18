import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

/**
 * Input — design-system text field.
 *
 * Implements the input fields section of /DESIGN.md.
 *
 * Structure (per design):
 * - Label is ALWAYS placed above the input (not as placeholder
 *   text) for accessibility. The label is wired via the
 *   [for] attribute; the [id] of the input is set by the
 *   consumer (typical pattern: derive from formControlName).
 * - Default border is surface-container-highest (1px).
 * - Focus border is primary (1px) with a subtle outer glow.
 * - Error state: error border + error text below the input.
 *
 * Usage:
 *   <app-input
 *     inputId="login-email"
 *     label="Email"
 *     type="email"
 *     [value]="email()"
 *     (valueChange)="email.set($event)"
 *     [error]="emailError()"
 *   />
 *
 * The component is intentionally a "dumb" wrapper: it does
 * NOT integrate with FormControl directly. The consumer
 * drives [value] and listens to (valueChange). This keeps
 * the component free of @angular/forms coupling and lets
 * it be reused in non-form contexts (filters, search
 * bars, etc.). For a FormControl integration, the consumer
 * wraps this in a small adapter component or uses a
 * `[formControl]` directive on the input directly.
 */
@Component({
  selector: 'app-input',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="space-y-1.5">
      @if (label(); as l) {
        <label [attr.for]="inputId()" class="block text-sm font-medium text-on-surface">
          {{ l }}
          @if (required()) {
            <span class="text-error" aria-hidden="true">*</span>
          }
        </label>
      }
      <input
        [id]="inputId()"
        [type]="type()"
        [value]="value()"
        [placeholder]="placeholder()"
        [disabled]="disabled()"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="error() ? inputId() + '-error' : hint() ? inputId() + '-hint' : null"
        [attr.aria-required]="required() ? 'true' : null"
        [class]="inputClasses()"
        (input)="onInput($event)"
      />
      @if (error(); as err) {
        <p [id]="inputId() + '-error'" role="alert" class="text-sm text-error">
          {{ err }}
        </p>
      } @else if (hint(); as h) {
        <p [id]="inputId() + '-hint'" class="text-sm text-on-surface-variant">
          {{ h }}
        </p>
      }
    </div>
  `,
})
export class InputComponent {
  readonly inputId = input.required<string>();
  readonly label = input<string | null>(null);
  readonly type = input<'text' | 'email' | 'password' | 'tel' | 'number' | 'url'>('text');
  readonly value = input<string>('');
  readonly placeholder = input<string>('');
  readonly disabled = input<boolean>(false);
  readonly required = input<boolean>(false);
  readonly error = input<string | null>(null);
  readonly hint = input<string | null>(null);

  /** Emitted on every input event. The new value is the
   * input element's current `.value` (DOM string). The
   * consumer decides what to do with it (set a signal, push
   * to a form control, etc.).
   */
  readonly valueChange = output<string>();

  protected readonly inputClasses = computed(() => {
    const hasError = !!this.error();
    const base =
      'block w-full rounded-md border bg-surface-container-lowest px-3 py-2 text-sm text-on-surface ' +
      'placeholder:text-on-surface-variant ' +
      'focus:outline-none focus:ring-1 focus:ring-offset-0 ' +
      'disabled:opacity-50 disabled:cursor-not-allowed';
    const state = hasError
      ? 'border-error focus:border-error focus:ring-error'
      : 'border-outline-variant focus:border-primary focus:ring-primary';
    return `${base} ${state}`;
  });

  protected onInput(event: Event): void {
    const target = event.target as HTMLInputElement;
    this.valueChange.emit(target.value);
  }
}
