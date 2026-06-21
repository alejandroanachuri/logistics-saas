import { TestBed } from '@angular/core/testing';

import {
  PasswordStrengthMeterComponent,
  computeStrength,
  StrengthTier as MeterTier,
  segmentsFilledForMeter,
  tierLabel,
} from './password-strength-meter';

describe('computeStrength (pure helper)', () => {
  /**
   * Per the design (§4.2 PR-4 PasswordStrengthMeterComponent),
   * the score is the count of these rules satisfied:
   * - length >= 8
   * - length >= 12  (separate bucket — extra credit for long pw)
   * - has uppercase
   * - has lowercase
   * - has digit
   * - has symbol (!@#$%^&*+-=?_ etc.)
   *
   * The total max is 6 (all rules pass). The tier mapping
   * compresses that into 4 visible bands.
   */
  it('returns 0 for an empty password', () => {
    expect(computeStrength('')).toBe(0);
  });

  it('counts character classes even for a too-short password (length rule is independent)', () => {
    // 'Ab1!' = 4 chars (< 8) but has upper + lower + digit + symbol = 4 points
    // This is intentional — the rules are independent so a short
    // mixed-class password still scores above zero.
    expect(computeStrength('Ab1!')).toBe(4);
  });

  it('returns 2 (Débil tier) for a long but class-poor password', () => {
    // 'abcdefgh' = 8 chars + lower only = 2 points = Débil
    expect(computeStrength('abcdefgh')).toBe(2);
  });

  it('returns 4 (Aceptable) for an 11-char mixed-case + digit password', () => {
    expect(computeStrength('Abcdefghij1')).toBe(4); // length-8 (no 12 yet) + upper + lower + digit
  });

  it('returns 5 (Buena) for a 12+ char mixed-case + digit password', () => {
    expect(computeStrength('Abcdefghijkl1')).toBe(5); // length-12 + upper + lower + digit
  });

  it('returns the maximum 6 for a long password with all 4 character classes', () => {
    expect(computeStrength('Abcdefghij1!')).toBe(6); // length-12 + upper + lower + digit + symbol
  });

  it('treats all the documented symbol characters as symbols', () => {
    const symbols = ['!', '@', '#', '$', '%', '^', '&', '*', '+', '-', '=', '?'];
    for (const s of symbols) {
      const pw = 'Abcdefghi' + s; // length-9 + upper + lower + symbol (no digit) = 4
      expect(computeStrength(pw)).toBe(4);
    }
  });
});

describe('tier mapping (pure helpers)', () => {
  it('maps score 0-2 to Débil', () => {
    expect(tierLabel(0)).toBe('Débil');
    expect(tierLabel(1)).toBe('Débil');
    expect(tierLabel(2)).toBe('Débil');
  });

  it('maps score 3-4 to Aceptable', () => {
    expect(tierLabel(3)).toBe('Aceptable');
    expect(tierLabel(4)).toBe('Aceptable');
  });

  it('maps score 5 to Buena', () => {
    expect(tierLabel(5)).toBe('Buena');
  });

  it('maps score 6 (max) to Fuerte', () => {
    expect(tierLabel(6)).toBe('Fuerte');
  });

  it('returns 0 segments for score 0', () => {
    expect(segmentsFilledForMeter(0)).toBe(0);
  });

  it('returns 1 segment for Débil', () => {
    expect(segmentsFilledForMeter(1)).toBe(1);
    expect(segmentsFilledForMeter(2)).toBe(1);
  });

  it('returns 2 segments for Aceptable', () => {
    expect(segmentsFilledForMeter(3)).toBe(2);
    expect(segmentsFilledForMeter(4)).toBe(2);
  });

  it('returns 3 segments for Buena', () => {
    expect(segmentsFilledForMeter(5)).toBe(3);
  });

  it('returns 4 segments for Fuerte', () => {
    expect(segmentsFilledForMeter(6)).toBe(4);
  });
});

describe('PasswordStrengthMeterComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [PasswordStrengthMeterComponent] });
  });

  it('renders no label and zero filled segments for an empty password', () => {
    const fixture = TestBed.createComponent(PasswordStrengthMeterComponent);
    fixture.componentRef.setInput('password', '');
    fixture.detectChanges();

    const meter = fixture.nativeElement.querySelector('[data-strength]') as HTMLElement;
    expect(meter.dataset['strength']).toBe('empty');
    expect(fixture.nativeElement.textContent).not.toContain('Débil');
    expect(fixture.nativeElement.textContent).not.toContain('Aceptable');
  });

  it('renders "Débil" and 1 segment for a weak 8-char password', () => {
    const fixture = TestBed.createComponent(PasswordStrengthMeterComponent);
    fixture.componentRef.setInput('password', 'abcdefgh'); // 8 chars, lower only = score 1 = Débil
    fixture.detectChanges();

    const meter = fixture.nativeElement.querySelector('[data-strength]') as HTMLElement;
    expect(meter.dataset['strength']).toBe('debil');
    expect(fixture.nativeElement.textContent).toContain('Débil');
  });

  it('renders "Fuerte" and 4 segments for a long password with all character classes', () => {
    const fixture = TestBed.createComponent(PasswordStrengthMeterComponent);
    fixture.componentRef.setInput('password', 'Abcdefghij1!');
    fixture.detectChanges();

    const meter = fixture.nativeElement.querySelector('[data-strength]') as HTMLElement;
    expect(meter.dataset['strength']).toBe('fuerte');
    expect(fixture.nativeElement.textContent).toContain('Fuerte');
  });
});
