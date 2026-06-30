import { maskCuit, maskDni, shouldMaskSensitiveFields } from './field-level-security';

describe('field-level-security', () => {
  describe('maskDni', () => {
    it('masks a typical 8-digit DNI keeping the first 2 + last 2', () => {
      expect(maskDni('12345678')).toBe('12***78');
    });

    it('masks a 7-digit DNI keeping the first 2 + last 2', () => {
      expect(maskDni('1234567')).toBe('12***67');
    });

    it('returns the input unchanged when shorter than 4 chars (nothing to mask)', () => {
      expect(maskDni('123')).toBe('123');
      expect(maskDni('1')).toBe('1');
      // Empty string is treated like null — no meaningful mask possible.
      expect(maskDni('')).toBeNull();
    });

    it('returns null for null / undefined inputs', () => {
      expect(maskDni(null)).toBeNull();
      expect(maskDni(undefined)).toBeNull();
    });
  });

  describe('maskCuit', () => {
    it('masks a typical CUIT keeping the prefix-2 + last-2 + check-digit', () => {
      expect(maskCuit('20123456789')).toBe('20-***89-9');
    });

    it('handles a CUIT that already contains dashes', () => {
      // The masker slices positions 0-2 + last-2 + last-1. When the
      // input already contains dashes the mask keeps the dashes
      // (they fall in the slice range). The contract is "first 2 +
      // masked middle + last 2 + check digit" — input formatting is
      // not normalised away.
      expect(maskCuit('20-12345678-9')).toBe('20-***-9-9');
    });

    it('returns the input unchanged when shorter than 4 chars', () => {
      expect(maskCuit('123')).toBe('123');
    });

    it('returns null for null / undefined inputs', () => {
      expect(maskCuit(null)).toBeNull();
      expect(maskCuit(undefined)).toBeNull();
    });
  });

  describe('shouldMaskSensitiveFields', () => {
    it('returns true for COMPANY_DRIVER', () => {
      expect(shouldMaskSensitiveFields(['COMPANY_DRIVER'])).toBe(true);
    });

    it('returns true for COMPANY_VIEWER', () => {
      expect(shouldMaskSensitiveFields(['COMPANY_VIEWER'])).toBe(true);
    });

    it('returns false for COMPANY_ADMIN / COMPANY_OPERATOR', () => {
      expect(shouldMaskSensitiveFields(['COMPANY_ADMIN'])).toBe(false);
      expect(shouldMaskSensitiveFields(['COMPANY_OPERATOR'])).toBe(false);
    });

    it('returns false for an empty / undefined role list', () => {
      expect(shouldMaskSensitiveFields([])).toBe(false);
      expect(shouldMaskSensitiveFields(undefined)).toBe(false);
    });

    it('returns true when ANY role in the list is elevated', () => {
      expect(shouldMaskSensitiveFields(['COMPANY_OPERATOR', 'COMPANY_VIEWER'])).toBe(true);
    });
  });
});
