package ar.com.logistics.shipment.tracking;

import java.security.SecureRandom;
import java.util.regex.Pattern;

/**
 * Generator for the public tracking-id format {@code LGST-XXXXXXXX}.
 *
 * <p>Per PRD §5.1:
 * <ul>
 *   <li>Format: {@code LGST-} + 8 characters in the Base32 Crockford
 *       alphabet (no {@code I L O U} to avoid human confusion).</li>
 *   <li>Alphabet: {@code 0123456789ABCDEFGHJKMNPQRSTVWXYZ} (32 symbols).</li>
 *   <li>Generation: cryptographically random
 *       ({@link SecureRandom}, never sequential).</li>
 *   <li>Uniqueness: GLOBAL across the platform. The DB has a UNIQUE
 *       constraint on {@code shipments.tracking_id}; this class does
 *       not enforce uniqueness — that's the caller's job (see
 *       {@link LgstGeneratorService} for the retry-with-collision
 *       behavior).</li>
 *   <li>Space: {@code 32^8 ≈ 1.1T} combos. Collision probability is
 *       effectively zero for any realistic tenant's monthly volume,
 *       but the retry loop is still required for correctness.</li>
 * </ul>
 *
 * <p>This class is a pure utility — no Spring, no state. The
 * {@link LgstGeneratorService} wraps it with the retry + audit
 * behavior.
 */
public final class LgstGenerator {

    /** Crockford Base32 (excludes I, L, O, U for human readability). */
    public static final String CROCKFORD_BASE32 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    /** Number of random suffix characters in the tracking id. */
    public static final int SUFFIX_LENGTH = 8;

    /** Prefix that distinguishes a LogiStart tracking id. */
    public static final String PREFIX = "LGST-";

    /**
     * Canonical regex: prefix + 8 Crockford chars, case-insensitive at
     * the matching site but the generator emits uppercase. Exposed for
     * validation in DTOs / controllers.
     */
    public static final Pattern TRACKING_ID_REGEX = Pattern.compile("^LGST-[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$");

    private static final SecureRandom RANDOM = new SecureRandom();

    private LgstGenerator() {
        // static utility
    }

    /**
     * Generate a single tracking id. Does NOT check uniqueness — the
     * caller (typically {@link LgstGeneratorService}) wraps this in a
     * retry loop against the DB UNIQUE constraint.
     *
     * @return a string like {@code "LGST-7K2M9XQP"}.
     */
    public static String generate() {
        StringBuilder sb = new StringBuilder(PREFIX.length() + SUFFIX_LENGTH);
        sb.append(PREFIX);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(CROCKFORD_BASE32.charAt(RANDOM.nextInt(CROCKFORD_BASE32.length())));
        }
        return sb.toString();
    }

    /**
     * Validate a tracking id string against the canonical regex.
     * Returns {@code true} iff the input is non-null and matches
     * {@link #TRACKING_ID_REGEX}.
     */
    public static boolean isValid(String trackingId) {
        return trackingId != null && TRACKING_ID_REGEX.matcher(trackingId).matches();
    }
}
