package ar.com.logistics.common.validation;

import java.util.regex.Pattern;

/**
 * Static utility for company-user username format checks (PRD line 552).
 *
 * <p>Rules:
 * <ul>
 *   <li>Length 3-30 characters.</li>
 *   <li>Lowercase ASCII letters, digits, and {@code _}, {@code .}, {@code -}.</li>
 *   <li>First character must be a letter (not a digit, not a separator).</li>
 * </ul>
 *
 * <p>Per-tenant uniqueness is enforced separately by the registration
 * service against the {@code company_users} table.
 */
public final class UsernameValidator {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9._-]{2,29}$");

    private UsernameValidator() {
        // static utility
    }

    public static boolean isValid(String username) {
        if (username == null) {
            return false;
        }
        return FORMAT.matcher(username).matches();
    }
}
