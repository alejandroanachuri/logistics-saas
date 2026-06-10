package ar.com.logistics.common.validation;

/**
 * Static utility for Argentine CUIT (and CUIL) format + check-digit
 * validation. Algorithm: modulo 11 over the 10 leading digits with
 * the AFIP weight vector {@code 5,4,3,2,7,6,5,4,3,2}.
 *
 * <p>Source: AFIP general resolution, mirrored in the PRD
 * (line 521 cites {@code 30-71234567-8} as the canonical example).
 */
public final class CuitValidator {

    /** AFIP mod-11 weight vector (left-to-right, 10 digits). */
    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private CuitValidator() {
        // static utility
    }

    /**
     * Check-digit algorithm. Input is the first 10 digits of a CUIT
     * (the prefix) and the function returns the canonical verifier
     * digit (0-9). If the mod-11 arithmetic yields 10, the canonical
     * substitution is 9; if it yields 11, the digit is 0.
     */
    public static int calculateVerifierDigit(String tenDigits) {
        if (tenDigits == null || tenDigits.length() != 10) {
            throw new IllegalArgumentException("expected exactly 10 digits, got: " + tenDigits);
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = tenDigits.charAt(i);
            if (!Character.isDigit(c)) {
                throw new IllegalArgumentException("non-digit at position " + i + ": " + c);
            }
            sum += (c - '0') * WEIGHTS[i];
        }
        int mod = sum % 11;
        int digit = 11 - mod;
        if (digit == 11) {
            return 0;
        }
        if (digit == 10) {
            return 9;
        }
        return digit;
    }

    /**
     * Full validity check. Accepts both {@code 30-71234567-8} and
     * {@code 30712356780} forms.
     */
    public static boolean isValid(String cuit) {
        String normalized = normalize(cuit);
        if (normalized == null) {
            return false;
        }
        int expected = calculateVerifierDigit(normalized.substring(0, 10));
        int actual = normalized.charAt(10) - '0';
        return expected == actual;
    }

    /**
     * Strip dashes and return the 11-digit canonical form, or
     * {@code null} if the input is not a valid 11-digit string.
     */
    public static String normalize(String cuit) {
        if (cuit == null) {
            return null;
        }
        String stripped = cuit.replace("-", "").trim();
        if (stripped.length() != 11) {
            return null;
        }
        for (int i = 0; i < 11; i++) {
            if (!Character.isDigit(stripped.charAt(i))) {
                return null;
            }
        }
        return stripped;
    }
}
