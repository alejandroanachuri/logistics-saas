package ar.com.logistics.shipment.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EventHashCalculator}. Covers SHA-256 determinism,
 * the 5-minute rounding window, sorted-key canonicalization, and the
 * null-argument contract.
 */
class EventHashCalculatorTest {

    private static final UUID PACKAGE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_PACKAGE_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant TIMESTAMP = Instant.parse("2026-06-25T10:00:00Z");

    private static Map<String, Object> payload(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    // ------------------------------------------------------------------------
    // Determinism.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Same inputs produce the same hash (deterministic)")
    void same_inputs_same_hash() {
        Map<String, Object> p = payload("weight", "5kg", "destination", "BUE");
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, TIMESTAMP);
        String h2 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, TIMESTAMP);
        assertThat(h1).isEqualTo(h2);
    }

    // ------------------------------------------------------------------------
    // Sensitivity.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Different packageId produces a different hash")
    void different_package_id_yields_different_hash() {
        Map<String, Object> p = payload("weight", "5kg");
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, TIMESTAMP);
        String h2 = EventHashCalculator.computeHash(OTHER_PACKAGE_ID, "package_created", p, TIMESTAMP);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("Different eventType produces a different hash")
    void different_event_type_yields_different_hash() {
        Map<String, Object> p = payload("weight", "5kg");
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, TIMESTAMP);
        String h2 = EventHashCalculator.computeHash(PACKAGE_ID, "package_in_transit", p, TIMESTAMP);
        assertThat(h1).isNotEqualTo(h2);
    }

    @Test
    @DisplayName("Different payload produces a different hash")
    void different_payload_yields_different_hash() {
        Map<String, Object> p1 = payload("weight", "5kg");
        Map<String, Object> p2 = payload("weight", "6kg");
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p1, TIMESTAMP);
        String h2 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p2, TIMESTAMP);
        assertThat(h1).isNotEqualTo(h2);
    }

    // ------------------------------------------------------------------------
    // 5-minute rounding window.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Timestamps within the same 5-minute bucket produce the same hash (rounding window)")
    void timestamps_within_window_same_hash() {
        Map<String, Object> p = payload("weight", "5kg");
        Instant t1 = Instant.parse("2026-06-25T10:00:00Z");
        Instant t2 = Instant.parse("2026-06-25T10:04:00Z"); // 4m later, same 5-min bucket
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, t1);
        String h2 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, t2);
        assertThat(h1).isEqualTo(h2);
    }

    @Test
    @DisplayName("Timestamps in adjacent 5-minute buckets produce different hashes (window boundary)")
    void timestamps_across_window_different_hash() {
        Map<String, Object> p = payload("weight", "5kg");
        Instant t1 = Instant.parse("2026-06-25T10:00:00Z");
        Instant t2 = Instant.parse("2026-06-25T10:05:00Z"); // next bucket
        String h1 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, t1);
        String h2 = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, t2);
        assertThat(h1).isNotEqualTo(h2);
    }

    // ------------------------------------------------------------------------
    // Sorted-key canonicalization.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Payload key order does not affect the hash (sorted canonicalization)")
    void key_order_does_not_affect_hash() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("destination", "BUE");
        a.put("weight", "5kg");
        a.put("carrier", "transify");

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("carrier", "transify");
        b.put("destination", "BUE");
        b.put("weight", "5kg");

        Map<String, Object> c = new TreeMap<>(a);

        String ha = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", a, TIMESTAMP);
        String hb = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", b, TIMESTAMP);
        String hc = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", c, TIMESTAMP);

        assertThat(ha).isEqualTo(hb).isEqualTo(hc);
    }

    // ------------------------------------------------------------------------
    // Empty payload.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Empty payload produces a valid 64-char hex hash (not null)")
    void empty_payload_produces_valid_hash() {
        Map<String, Object> empty = new HashMap<>();
        String h = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", empty, TIMESTAMP);
        assertThat(h).isNotNull();
        assertThat(h).hasSize(64);
        assertThat(h).matches("^[0-9a-f]{64}$");
    }

    // ------------------------------------------------------------------------
    // Null-argument contract.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Throws IllegalArgumentException when packageId is null")
    void null_package_id_throws() {
        Map<String, Object> p = payload("weight", "5kg");
        assertThatThrownBy(() -> EventHashCalculator.computeHash(null, "package_created", p, TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when eventType is null")
    void null_event_type_throws() {
        Map<String, Object> p = payload("weight", "5kg");
        assertThatThrownBy(() -> EventHashCalculator.computeHash(PACKAGE_ID, null, p, TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when payload is null")
    void null_payload_throws() {
        assertThatThrownBy(() -> EventHashCalculator.computeHash(PACKAGE_ID, "package_created", null, TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Throws IllegalArgumentException when eventTimestamp is null")
    void null_timestamp_throws() {
        Map<String, Object> p = payload("weight", "5kg");
        assertThatThrownBy(() -> EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ------------------------------------------------------------------------
    // Format.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Output is always a 64-character lowercase hex string")
    void output_format_is_64_char_lowercase_hex() {
        Map<String, Object> p = payload("weight", "5kg", "destination", "BUE");
        String h = EventHashCalculator.computeHash(PACKAGE_ID, "package_created", p, TIMESTAMP);
        assertThat(h).hasSize(64).matches("^[0-9a-f]+$");
    }
}
