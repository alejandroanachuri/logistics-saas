package ar.com.logistics.common.validation;

/**
 * Static utility for password policy checks (PRD line 551).
 *
 * <p>v1 policy:
 * <ul>
 *   <li>Length ≥ 8 characters.</li>
 *   <li>At least one uppercase letter.</li>
 *   <li>At least one lowercase letter.</li>
 *   <li>At least one digit.</li>
 * </ul>
 *
 * <p>v2 considerations (special chars, length tiers) are out of scope.
 */
public final class PasswordValidator {

    private PasswordValidator() {
        // static utility
    }

    /** Format check only. Does NOT enforce BCrypt hashing. */
    public static boolean isValid(String password) {
        if (password == null || password.length() < 8) {
            return false;
        }
        boolean hasUpper = false;
        boolean hasLower = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                hasUpper = true;
            } else if (Character.isLowerCase(c)) {
                hasLower = true;
            } else if (Character.isDigit(c)) {
                hasDigit = true;
            }
        }
        return hasUpper && hasLower && hasDigit;
    }
}
