package ar.com.logistics.common.validation;

import java.util.regex.Pattern;

/**
 * Static utility for tenant slug format and reserved-name checks.
 *
 * <p>Rules (PRD line 548):
 * <ul>
 *   <li>Length 2-12 characters.</li>
 *   <li>Only lowercase ASCII letters and digits.</li>
 *   <li>First character must be a letter.</li>
 *   <li>Must not be in the reserved-slugs catalog.</li>
 * </ul>
 *
 * <p>The format check is a pure function ({@link #isValidFormat}). The
 * reserved-name check ({@link #isReserved}) is a constant-time
 * lookup against a hardcoded set for v1; the DB-backed
 * {@code public.reserved_slugs} catalog is consulted at write time by
 * the registration service.
 */
public final class SlugValidator {

    private static final Pattern FORMAT = Pattern.compile("^[a-z][a-z0-9]{1,11}$");

    private static final java.util.Set<String> RESERVED = java.util.Set.of(
            "admin",
            "api",
            "app",
            "www",
            "system",
            "support",
            "help",
            "login",
            "register",
            "auth",
            "static",
            "public",
            "root",
            "test",
            "demo");

    private SlugValidator() {
        // static utility
    }

    /**
     * Format-only check. Does not consult the reserved list.
     *
     * @return true when {@code slug} is non-null, 2-12 characters,
     *         starts with a letter, and contains only lowercase
     *         letters and digits.
     */
    public static boolean isValidFormat(String slug) {
        if (slug == null) {
            return false;
        }
        return FORMAT.matcher(slug).matches();
    }

    /**
     * Reserved-name check. Case-insensitive; a slug of {@code "Admin"}
     * is treated the same as {@code "admin"}.
     */
    public static boolean isReserved(String slug) {
        if (slug == null) {
            return false;
        }
        return RESERVED.contains(slug.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Convenience: format AND not-reserved. The full check the
     * registration service runs before INSERTing a tenant.
     */
    public static boolean isAcceptable(String slug) {
        return isValidFormat(slug) && !isReserved(slug);
    }
}
