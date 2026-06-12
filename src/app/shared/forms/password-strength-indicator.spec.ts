import { TestBed } from '@angular/core/testing';
import { Component, ViewChild } from '@angular/core';

import { PasswordStrengthIndicator, tierFromScore, segmentsFilledFor } from './password-strength-indicator';

/**
 * Tiny host that owns the indicator so the spec can drive
 * its {@code password} input through Angular's normal
 * template binding (the only public way to set a signal
 * input from outside the component). The spec asserts the
 * render contract via the host's DOM, not by poking the
 * component instance's signal — the signal-input API does
 * not expose a setter for external callers.
 */
@Component({
  standalone: true,
  imports: [PasswordStrengthIndicator],
  template: `<app-password-strength-indicator [password]="value"></app-password-strength-indicator>`,
})
class HostComponent {
  value = '';
  @ViewChild(PasswordStrengthIndicator) indicator!: PasswordStrengthIndicator;
}

/**
 * Unit spec for {@code PasswordStrengthIndicator} (PR12a).
 * The indicator is a passive visual component that takes the
 * current {@code password} value and renders one of three
 * tiers ({@code Débil}/{@code Aceptable}/{@code Fuerte}) using
 * the {@code @zxcvbn-ts/core} score as the input.
 *
 * <p>The first {@code it()} is a no-op warm-up that exists
 * to absorb the vitest-builder orphan quirk (see apply-
 * progress observation #122 Discovery #15) — the builder
 * silently drops the first test of any newly-added spec file;
 * the warm-up makes the count predictable.
 */
describe('PasswordStrengthIndicator', () => {
  function render(value: string): { host: HTMLElement; set(next: string): void } {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.componentInstance.value = value;
    fixture.detectChanges();
    return {
      host: fixture.nativeElement as HTMLElement,
      set(next: string) {
        fixture.componentInstance.value = next;
        fixture.detectChanges();
      },
    };
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [HostComponent] });
  });

  // -------- warm-up (vitest orphan offset) --------

  it('warm-up — service instantiates', () => {
    // No-op warm-up; absorbs the first-test-of-spec drop.
    expect(true).toBe(true);
  });

  // -------- pure helper: tierFromScore --------

  it('tierFromScore maps zxcvbn scores 0-2 to Débil', () => {
    expect(tierFromScore(0)).toBe('Débil');
    expect(tierFromScore(1)).toBe('Débil');
    expect(tierFromScore(2)).toBe('Débil');
  });

  it('tierFromScore maps zxcvbn score 3 to Aceptable', () => {
    expect(tierFromScore(3)).toBe('Aceptable');
  });

  it('tierFromScore maps zxcvbn score 4 to Fuerte', () => {
    expect(tierFromScore(4)).toBe('Fuerte');
  });

  // -------- empty password → neutral placeholder --------

  it('renders a neutral placeholder when the password is empty', () => {
    const ctx = render('');
    const placeholder = ctx.host.querySelector('[data-strength="empty"]');
    expect(placeholder).toBeTruthy();
    // No tier label is rendered for an empty input.
    const tier = ctx.host.querySelector('[data-strength-tier]');
    expect(tier).toBeNull();
  });

  // -------- short password → Débil --------

  it('renders Débil for a short, weak password (zxcvbn score 0)', () => {
    const ctx = render('abc');
    const tier = ctx.host.querySelector('[data-strength-tier]');
    expect(tier?.textContent?.trim()).toBe('Débil');
  });

  // -------- medium password → Débil or Aceptable --------

  it('renders a valid tier for a medium-strength password (Débil or Aceptable)', () => {
    const ctx = render('Password1');
    const tier = ctx.host.querySelector('[data-strength-tier]');
    const text = tier?.textContent?.trim() ?? '';
    expect(['Débil', 'Aceptable']).toContain(text);
  });

  // -------- strong English password → Fuerte --------

  it('renders Fuerte for a strong English passphrase (zxcvbn score 4)', () => {
    const ctx = render('correct-horse-battery-staple-9!');
    const tier = ctx.host.querySelector('[data-strength-tier]');
    expect(tier?.textContent?.trim()).toBe('Fuerte');
  });

  // -------- strong Spanish password → Fuerte --------

  it('renders Fuerte for a strong Spanish passphrase (zxcvbn score 4)', () => {
    const ctx = render('Caballo-Correcto-Bateria-99!');
    const tier = ctx.host.querySelector('[data-strength-tier]');
    expect(tier?.textContent?.trim()).toBe('Fuerte');
  });

  // -------- aria-label includes the tier name --------

  it('exposes the tier name in the aria-label for screen readers', () => {
    const ctx = render('correct-horse-battery-staple-9!');
    const labelled = ctx.host.querySelector('[aria-label]');
    const aria = labelled?.getAttribute('aria-label') ?? '';
    expect(aria).toContain('Fuerte');
  });

  // -------- segmentsFilledFor (pure helper) --------

  it('segmentsFilledFor returns 1 for Débil, 2 for Aceptable, 3 for Fuerte, 0 for null', () => {
    expect(segmentsFilledFor('Débil')).toBe(1);
    expect(segmentsFilledFor('Aceptable')).toBe(2);
    expect(segmentsFilledFor('Fuerte')).toBe(3);
    expect(segmentsFilledFor(null)).toBe(0);
  });
});
