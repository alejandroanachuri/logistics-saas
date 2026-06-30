package ar.com.logistics.shipment.tracking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LgstGenerator}. Covers the public regex, the
 * SecureRandom-based generator, and the Crockford Base32 exclusions
 * (I, L, O, U).
 */
class LgstGeneratorTest {

    private static final String VALID_REGEX = "^LGST-[0-9A-HJKMNP-TV-Z]{8}$";

    // ------------------------------------------------------------------------
    // generate()
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("generate() returns a string matching the canonical regex")
    void generate_matches_regex() {
        String id = LgstGenerator.generate();
        assertThat(id).matches(VALID_REGEX);
        assertThat(id).hasSize("LGST-XXXXXXXX".length()); // 13 chars total
    }

    @Test
    @DisplayName("generate() returns 1000 unique ids (no collisions in the tested sample)")
    void generate_is_random_and_unique() {
        // 32^8 ≈ 1.1T combos — collision probability for 1000 samples is
        // effectively zero (~5e-10). Any duplicate in 1000 samples means
        // the generator is broken.
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(LgstGenerator.generate());
        }
        assertThat(seen).hasSize(1000);
    }

    // ------------------------------------------------------------------------
    // isValid() — happy path.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValid accepts a tracking id with Crockford alphabetic characters")
    void is_valid_accepts_crockford_alphabetic() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQP")).isTrue();
    }

    @Test
    @DisplayName("isValid accepts an all-zero tracking id (zeros are valid Crockford)")
    void is_valid_accepts_all_zero() {
        assertThat(LgstGenerator.isValid("LGST-00000000")).isTrue();
    }

    @Test
    @DisplayName("isValid accepts a digit-only tracking id")
    void is_valid_accepts_digits_only() {
        assertThat(LgstGenerator.isValid("LGST-12345678")).isTrue();
    }

    // ------------------------------------------------------------------------
    // isValid() — case sensitivity.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValid rejects a tracking id with lowercase characters")
    void is_valid_rejects_lowercase() {
        // The regex is case-sensitive at the matching site (no
        // Pattern.CASE_INSENSITIVE flag). Both prefix and suffix must
        // be uppercase.
        assertThat(LgstGenerator.isValid("lgst-7k2m9xqp")).isFalse();
        assertThat(LgstGenerator.isValid("LGST-7k2m9xqp")).isFalse();
    }

    // ------------------------------------------------------------------------
    // isValid() — Crockford alphabet exclusions (I, L, O, U).
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValid rejects tracking ids containing I (excluded from Crockford)")
    void is_valid_rejects_I() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQI")).isFalse();
    }

    @Test
    @DisplayName("isValid rejects tracking ids containing L (excluded from Crockford)")
    void is_valid_rejects_L() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQL")).isFalse();
    }

    @Test
    @DisplayName("isValid rejects tracking ids containing O (excluded from Crockford)")
    void is_valid_rejects_O() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQO")).isFalse();
    }

    @Test
    @DisplayName("isValid rejects tracking ids containing U (excluded from Crockford)")
    void is_valid_rejects_U() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQU")).isFalse();
    }

    // ------------------------------------------------------------------------
    // isValid() — wrong length.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValid rejects tracking ids with fewer than 8 suffix characters")
    void is_valid_rejects_too_short() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQ")).isFalse(); // 7 chars
    }

    @Test
    @DisplayName("isValid rejects tracking ids with more than 8 suffix characters")
    void is_valid_rejects_too_long() {
        assertThat(LgstGenerator.isValid("LGST-7K2M9XQPP")).isFalse(); // 9 chars
    }

    // ------------------------------------------------------------------------
    // isValid() — degenerate inputs.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("isValid rejects random non-tracking strings")
    void is_valid_rejects_random_strings() {
        assertThat(LgstGenerator.isValid("XYZ")).isFalse();
        assertThat(LgstGenerator.isValid("hello world")).isFalse();
        assertThat(LgstGenerator.isValid("ABCDEFGH")).isFalse(); // no LGST- prefix
        assertThat(LgstGenerator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for null")
    void is_valid_rejects_null() {
        assertThat(LgstGenerator.isValid(null)).isFalse();
    }

    // ------------------------------------------------------------------------
    // Round-trip: every generated id is valid.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Every generated id passes its own isValid check")
    void generated_ids_are_self_validating() {
        for (int i = 0; i < 50; i++) {
            String id = LgstGenerator.generate();
            assertThat(LgstGenerator.isValid(id))
                    .as("Generated id '%s' should match its own regex", id)
                    .isTrue();
        }
    }
}
