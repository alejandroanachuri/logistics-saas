package ar.com.logistics.common.validation;

/**
 * Static utility for Argentine DNI format validation.
 *
 * <p>Per PRD §6.2 (etapa-3-envios), a {@code person_type = FISICA}
 * customer requires a DNI of 7 or 8 digits. The PRD leaves the actual
 * format check at the application layer (no DB CHECK that strips
 * leading zeros, etc.), so this utility provides:
 *
 * <ul>
 *   <li>{@link #isValid(String)} — accepts a 7 or 8 digit string</li>
 *   <li>{@link #normalize(String)} — strips non-digits, returns the
 *       canonical 7- or 8-digit form or {@code null}</li>
 * </ul>
 *
 * <p>Duplicate DNIs within the same tenant are blocked at the service
 * layer via {@code existsByTenantIdAndDni} (app-layer uniqueness check,
 * no DB UNIQUE constraint — per PRD §6.2 line 247 the index is
 * non-unique). CUIT validation lives in {@link CuitValidator}.
 */
public final class DniValidator {

    private DniValidator() {
        // static utility
    }

    /** Length bounds per PRD §6.2 + RN-LOGI-011. */
    private static final int MIN_LENGTH = 7;

    private static final int MAX_LENGTH = 8;

    /**
     * Validity check. Accepts the canonical 7- or 8-digit form. Strips
     * whitespace before testing. Returns {@code false} for any input
     * that doesn't normalize to 7 or 8 digits.
     */
    public static boolean isValid(String dni) {
        String normalized = normalize(dni);
        if (normalized == null) {
            return false;
        }
        int len = normalized.length();
        return len == MIN_LENGTH || len == MAX_LENGTH;
    }

    /**
     * Strip whitespace and non-digit characters, return the canonical
     * 7- or 8-digit form, or {@code null} if the input doesn't
     * normalize to a 7- or 8-digit string.
     */
    public static String normalize(String dni) {
        if (dni == null) {
            return null;
        }
        String stripped = dni.replaceAll("\\s+", "").replaceAll("\\D+", "");
        if (stripped.length() != MIN_LENGTH && stripped.length() != MAX_LENGTH) {
            return null;
        }
        return stripped;
    }
}
