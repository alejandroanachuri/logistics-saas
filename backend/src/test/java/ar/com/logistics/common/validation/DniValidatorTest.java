package ar.com.logistics.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DniValidator}. Per PRD §6.2 + RN-LOGI-011, a
 * {@code person_type = FISICA} customer requires a 7- or 8-digit DNI.
 */
class DniValidatorTest {

    @Test
    @DisplayName("isValid returns true for an 8-digit DNI")
    void is_valid_accepts_eight_digits() {
        assertThat(DniValidator.isValid("30123456")).isTrue();
    }

    @Test
    @DisplayName("isValid returns true for a 7-digit DNI")
    void is_valid_accepts_seven_digits() {
        assertThat(DniValidator.isValid("1234567")).isTrue();
    }

    @Test
    @DisplayName("isValid returns false for a 6-digit DNI (too short)")
    void is_valid_rejects_too_short() {
        assertThat(DniValidator.isValid("123456")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for a 9-digit DNI (too long)")
    void is_valid_rejects_too_long() {
        assertThat(DniValidator.isValid("123456789")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for the empty string")
    void is_valid_rejects_empty() {
        assertThat(DniValidator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("isValid returns false for null")
    void is_valid_rejects_null() {
        assertThat(DniValidator.isValid(null)).isFalse();
    }

    @Test
    @DisplayName("isValid returns false when the digit count after stripping is not 7-8")
    void is_valid_rejects_non_digits() {
        // The regex strips any non-digit (\D+), so dotted/dashed/spaced
        // forms NORMALIZE to a valid length and pass. Only inputs where
        // the remaining digit count is wrong should be rejected.
        assertThat(DniValidator.isValid("1234abc")).isFalse(); // 4 digits after strip
        assertThat(DniValidator.isValid("abcdefgh")).isFalse(); // 0 digits after strip
        assertThat(DniValidator.isValid("1a2b3c4d5")).isFalse(); // 5 digits after strip
    }

    @Test
    @DisplayName("isValid strips whitespace and accepts an 8-digit DNI with spaces")
    void is_valid_strips_whitespace() {
        assertThat(DniValidator.isValid("12 345 678")).isTrue();
        assertThat(DniValidator.isValid(" 12345678 ")).isTrue();
    }

    @Test
    @DisplayName("normalize strips dashes and returns the canonical 8-digit form")
    void normalize_strips_dashes() {
        // The regex strips non-digits AFTER stripping whitespace, so
        // dashes are removed too. Verify both whitespace and dash paths.
        assertThat(DniValidator.normalize("12-345-678")).isEqualTo("12345678");
        assertThat(DniValidator.normalize("12 345 678")).isEqualTo("12345678");
        assertThat(DniValidator.normalize("12345678")).isEqualTo("12345678");
    }

    @Test
    @DisplayName("normalize returns null when the stripped value has the wrong length")
    void normalize_returns_null_for_wrong_length() {
        assertThat(DniValidator.normalize("123")).isNull(); // 3 digits
        assertThat(DniValidator.normalize("123456789")).isNull(); // 9 digits
        assertThat(DniValidator.normalize(null)).isNull();
        assertThat(DniValidator.normalize("")).isNull();
    }
}
