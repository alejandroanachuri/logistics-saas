import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { zxcvbn, ZxcvbnResult } from '@zxcvbn-ts/core';

/**
 * Visual strength tier surfaced by
 * {@code PasswordStrengthIndicator}. The three Spanish labels
 * match the wizard spec ({@code wizard-registration.md} §Step
 * 2 — Admin User Data Form): {@code Débil}, {@code Aceptable},
 * {@code Fuerte}.
 */
export type StrengthTier = 'Débil' | 'Aceptable' | 'Fuerte';

/**
 * Map a {@code zxcvbn} score (0..4) to the v1 three-tier
 * label the wizard renders:
 *
 * <ul>
 *   <li>0-2: {@code Débil} (red, 1 segment filled)</li>
 *   <li>3:   {@code Aceptable} (yellow, 2 segments filled)</li>
 *   <li>4:   {@code Fuerte} (green, 3 segments filled)</li>
 * </ul>
 *
 * Pure function — easy to unit-test without a TestBed.
 */
export function tierFromScore(score: number): StrengthTier {
  if (score >= 4) return 'Fuerte';
  if (score >= 3) return 'Aceptable';
  return 'Débil';
}

/**
 * Number of segments to fill for a given tier. Mirrors the
 * mapping in the template.
 */
export function segmentsFilledFor(tier: StrengthTier | null): 0 | 1 | 2 | 3 {
  if (tier === 'Fuerte') return 3;
  if (tier === 'Aceptable') return 2;
  if (tier === 'Débil') return 1;
  return 0;
}

/**
 * Standalone password strength indicator. Receives the
 * current password value as a signal input and renders one
 * of three visual tiers
 * ({@code Débil}/{@code Aceptable}/{@code Fuerte}) using
 * {@code @zxcvbn-ts/core} as the underlying scorer.
 *
 * <p>The component is purely presentational — it does NOT
 * own the password form control, the validators, or the
 * submit flow. The wizard's step 2 component hosts both the
 * password input and this indicator side-by-side; the
 * indicator subscribes to the password value via the
 * {@code password} input binding.
 *
 * <p>Empty / short passwords render a neutral placeholder
 * (no tier label, no colored segments) so the layout does
 * not jump when the user first focuses the input. The
 * placeholder exposes {@code data-strength="empty"} so the
 * spec can assert the no-tier state.
 */
@Component({
  selector: 'app-password-strength-indicator',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './password-strength-indicator.html',
})
export class PasswordStrengthIndicator {
  /**
   * The current password value. Bound by the parent (the
   * wizard's step 2 form group) so the indicator re-renders
   * on every keystroke.
   */
  readonly password = input<string>('');

  /**
   * Cached zxcvbn result for the current password. {@code
   * null} for an empty / whitespace-only value, which keeps
   * the render layer free of try/catch noise.
   */
  private readonly result = computed<ZxcvbnResult | null>(() => {
    const value = this.password();
    if (value.length === 0) return null;
    return zxcvbn(value);
  });

  /**
   * {@code null} when the input is empty (no tier rendered);
   * otherwise one of the three {@code StrengthTier} values.
   */
  readonly tier = computed<StrengthTier | null>(() => {
    const r = this.result();
    return r === null ? null : tierFromScore(r.score);
  });

  /**
   * Number of segments to color in the bar (0-3). 0 means
   * "render the neutral placeholder".
   */
  readonly filledSegments = computed<0 | 1 | 2 | 3>(() =>
    segmentsFilledFor(this.tier()),
  );

  /**
   * Accessible label for the indicator region, e.g.
   * {@code "Fuerza de la contraseña: Fuerte"}. Falls back to
   * the neutral copy when the input is empty.
   */
  readonly ariaLabel = computed<string>(() => {
    const t = this.tier();
    return t === null
      ? 'Fuerza de la contraseña: sin ingresar'
      : `Fuerza de la contraseña: ${t}`;
  });
}
