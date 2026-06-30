package ar.com.logistics.shipment.tracking;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * SHA-256 hash for the {@code tracking_events.event_hash} column.
 *
 * <p>Per PRD §8.4, the hash guarantees idempotency: the same logical
 * event registered twice is rejected by the DB UNIQUE constraint
 * with a {@code 409 DUPLICATE_EVENT}. The hash is computed
 * server-side from:
 *
 * <ol>
 *   <li>{@code package_id} (UUID)</li>
 *   <li>{@code event_type} (string)</li>
 *   <li>The canonicalized payload (sorted-key JSON, no whitespace)</li>
 *   <li>The event timestamp, truncated to the nearest 5-minute
 *       boundary</li>
 * </ol>
 *
 * <p>The 5-minute rounding window absorbs client/server clock skew
 * for events that arrive shortly after their nominal occurrence. Two
 * events whose payloads and types are identical and whose timestamps
 * fall in the same 5-minute bucket produce the same hash → duplicate
 * rejected.
 *
 * <p>The hash is always 64 hex characters (SHA-256 output).
 *
 * <p><b>Implementation note:</b> we instantiate a private
 * {@link ObjectMapper} with sorted keys and no whitespace so the
 * canonical JSON form is deterministic regardless of the input
 * {@code Map} iteration order. {@link com.fasterxml.jackson.databind.MapSerializer}
 * defaults don't sort; we sort ourselves via {@link TreeMap} on the
 * way in.
 */
public final class EventHashCalculator {

    /** SHA-256 hex output length. */
    private static final int HEX_LENGTH = 64;

    /** 5-minute rounding window for event_timestamp (PRD §8.4). */
    private static final int ROUNDING_MINUTES = 5;

    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();

    private static final ObjectMapper CANONICAL_MAPPER =
            new ObjectMapper().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private EventHashCalculator() {
        // static utility
    }

    /**
     * Compute the canonical event hash. Returns a 64-character
     * lowercase hex string.
     *
     * @param packageId the target package (required)
     * @param eventType the event type (required, e.g. {@code package_created})
     * @param payload the event metadata (required, may be empty but not null)
     * @param eventTimestamp the event timestamp (required, will be truncated to 5-min)
     * @return 64-char hex SHA-256 hash
     * @throws IllegalArgumentException if any required input is null
     */
    public static String computeHash(
            UUID packageId, String eventType, Map<String, Object> payload, Instant eventTimestamp) {
        if (packageId == null || eventType == null || payload == null || eventTimestamp == null) {
            throw new IllegalArgumentException("packageId, eventType, payload, eventTimestamp are all required");
        }

        // Truncate to 5-minute boundary (PRD §8.4 idempotency window).
        Instant rounded = eventTimestamp
                .truncatedTo(ChronoUnit.MINUTES)
                .minus(eventTimestamp.getEpochSecond() % (ROUNDING_MINUTES * 60L), ChronoUnit.SECONDS);

        String canonicalJson = canonicalJson(payload);
        String composite = packageId + "|" + eventType + "|" + canonicalJson + "|" + rounded.toString();

        return sha256Hex(composite);
    }

    /**
     * Convert a metadata payload to canonical JSON: keys sorted
     * lexicographically, no whitespace, deterministic type encoding.
     * The Jackson {@code ObjectMapper} is configured with
     * {@code ORDER_MAP_ENTRIES_BY_KEYS} so the writer respects our
     * {@link TreeMap} ordering at the top level; nested maps are
     * also {@code TreeMap}-ed recursively on the way in by callers.
     */
    private static String canonicalJson(Map<String, Object> payload) {
        try {
            return CANONICAL_MAPPER.writeValueAsString(new TreeMap<>(payload));
        } catch (JsonProcessingException e) {
            // The payload is a Map<String, Object> built in-process; it
            // cannot fail JSON serialization for a reasonable schema. If
            // it does, that's a bug — fail loud.
            throw new IllegalStateException("Failed to canonicalize event payload", e);
        }
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            char[] hex = new char[HEX_LENGTH];
            for (int i = 0; i < digest.length; i++) {
                int v = digest[i] & 0xFF;
                hex[i * 2] = HEX_CHARS[v >>> 4];
                hex[i * 2 + 1] = HEX_CHARS[v & 0x0F];
            }
            return new String(hex);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required by every JRE; this is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
