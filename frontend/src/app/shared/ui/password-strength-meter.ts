import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

export type StrengthTier = 'Débil' | 'Aceptable' | 'Buena' | 'Fuerte';

const SYMBOL_PATTERN = /[!@#$%^&*+\-=?_]/;
const DIGIT_PATTERN = /[0-9]/;
const UPPER_PATTERN = /[A-Z]/;
const LOWER_PATTERN = /[a-z]/;

/**
 * Compute a password strength score from 0 to 6. Each rule
 * the password satisfies adds 1 point:
 * - length >= 8 (the backend's minimum)
 * - length >= 12 (extra credit for long passwords)
 * - contains an uppercase letter
 * - contains a lowercase letter
 * - contains a digit
 * - contains a symbol (!@#$%^&*+-=?_)
 *
 * Pure function — easy to unit-test without a TestBed.
 */
export function computeStrength(password: string): number {
  if (!password) return 0;
  let score = 0;
  if (password.length >= 8) score += 1;
  if (password.length >= 12) score += 1;
  if (UPPER_PATTERN.test(password)) score += 1;
  if (LOWER_PATTERN.test(password)) score += 1;
  if (DIGIT_PATTERN.test(password)) score += 1;
  if (SYMBOL_PATTERN.test(password)) score += 1;
  return score;
}

/**
 * Map a 0..6 score to the four visible tiers the meter
 * renders. Higher score => stronger tier label.
 */
export function tierLabel(score: number): StrengthTier {
  if (score >= 6) return 'Fuerte';
  if (score >= 5) return 'Buena';
  if (score >= 3) return 'Aceptable';
  return 'Débil';
}

/**
 * Number of segments to fill in the bar (0..4). Mirrors the
 * tier breakdown: 1 for Débil, 2 for Aceptable, 3 for Buena,
 * 4 for Fuerte, 0 when the score is 0 (empty / too-short).
 */
export function segmentsFilledForMeter(score: number): 0 | 1 | 2 | 3 | 4 {
  if (score >= 6) return 4;
  if (score >= 5) return 3;
  if (score >= 3) return 2;
  if (score >= 1) return 1;
  return 0;
}

/**
 * Palette for a given filled-segment count. Each tier picks
 * a token-based background for the filled segments.
 */
function paletteForTier(tier: StrengthTier): { barClass: string; textClass: string } {
  switch (tier) {
    case 'Fuerte':
      return { barClass: 'bg-tertiary', textClass: 'text-tertiary' };
    case 'Buena':
      return { barClass: 'bg-secondary', textClass: 'text-secondary' };
    case 'Aceptable':
      return { barClass: 'bg-warning', textClass: 'text-warning' };
    case 'Débil':
      return { barClass: 'bg-error', textClass: 'text-error' };
  }
}

/**
 * Map a tier to a {@code data-strength} attribute value
 * usable by tests + CSS.
 */
function dataStrengthFor(tier: StrengthTier | null): string {
  if (tier === null) return 'empty';
  return tier.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '');
}

/**
 * PasswordStrengthMeter — design-system strength indicator for
 * the team-create + reset-password forms. Renders a 4-segment
 * progress bar + tier label.
 *
 * <p>This is a separate primitive from
 * {@code PasswordStrengthIndicator} (used in the registration
 * wizard) because:
 * - The team-create form needs a stricter, deterministic rule
 *   set (length 8/12 + 4 character classes) — not zxcvbn's
 *   probabilistic scoring.
 * - The meter renders a 4-segment bar (Débil/Aceptable/Buena/
 *   Fuerte), not the 3-tier Débil/Aceptable/Fuerte the wizard
 *   uses.
 * - The meter sits beside a password input that the parent
 *   form owns; the meter just reads the value via signal input.
 *
 * <p>Empty passwords render a neutral placeholder
 * ({@code data-strength="empty"}) so the layout does not jump
 * when the input gains focus.
 */
@Component({
  selector: 'app-password-strength-meter',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div [attr.data-strength]="dataStrength()" class="space-y-1.5">
      <div class="flex gap-1" aria-hidden="true">
        @for (i of [0, 1, 2, 3]; track i) {
          <div
            [class]="segmentClass(i)"
            class="h-1 flex-1 rounded-full bg-surface-container-high"
          ></div>
        }
      </div>
      @if (label(); as l) {
        <p [class]="textClass()" class="text-xs font-semibold">{{ l }}</p>
      }
    </div>
  `,
})
export class PasswordStrengthMeterComponent {
  readonly password = input<string>('');

  protected readonly score = computed(() => computeStrength(this.password()));

  protected readonly tier = computed<StrengthTier | null>(() => {
    const s = this.score();
    if (s === 0) return null;
    return tierLabel(s);
  });

  protected readonly filledSegments = computed(() => segmentsFilledForMeter(this.score()));

  protected readonly dataStrength = computed(() => dataStrengthFor(this.tier()));

  protected readonly label = computed<string | null>(() => this.tier());

  protected readonly palette = computed(() => {
    const t = this.tier();
    return t === null
      ? { barClass: '', textClass: '' }
      : paletteForTier(t);
  });

  protected readonly textClass = computed(() => this.palette().textClass);

  protected segmentClass(index: number): string {
    return index < this.filledSegments() ? this.palette().barClass : '';
  }
}
