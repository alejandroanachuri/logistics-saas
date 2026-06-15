package ar.com.logistics.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SlugValidator}. Format check is the core;
 * the reserved-name check is a constant-time set lookup.
 */
class SlugValidatorTest {

    @Test
    @DisplayName("Accepts a valid 3-letter slug")
    void accepts_three_letter_slug() {
        assertThat(SlugValidator.isValidFormat("mvr")).isTrue();
        assertThat(SlugValidator.isAcceptable("mvr")).isTrue();
    }

    @Test
    @DisplayName("Accepts the full-length 12-character slug")
    void accepts_full_length_slug() {
        assertThat(SlugValidator.isValidFormat("abcdefghijkl")).isTrue();
    }

    @Test
    @DisplayName("Accepts a slug with letters and digits")
    void accepts_letters_and_digits() {
        assertThat(SlugValidator.isValidFormat("a1b2c3")).isTrue();
        assertThat(SlugValidator.isValidFormat("test123")).isTrue();
    }

    @Test
    @DisplayName("Rejects slugs shorter than 2 characters")
    void rejects_too_short() {
        assertThat(SlugValidator.isValidFormat("a")).isFalse();
        assertThat(SlugValidator.isValidFormat("")).isFalse();
        assertThat(SlugValidator.isValidFormat(null)).isFalse();
    }

    @Test
    @DisplayName("Rejects slugs longer than 12 characters")
    void rejects_too_long() {
        assertThat(SlugValidator.isValidFormat("abcdefghijklm")).isFalse(); // 13 chars
    }

    @Test
    @DisplayName("Rejects slugs with uppercase letters")
    void rejects_uppercase() {
        assertThat(SlugValidator.isValidFormat("MVR")).isFalse();
        assertThat(SlugValidator.isValidFormat("Mvr")).isFalse();
        assertThat(SlugValidator.isValidFormat("mvR")).isFalse();
    }

    @Test
    @DisplayName("Rejects slugs starting with a digit")
    void rejects_leading_digit() {
        assertThat(SlugValidator.isValidFormat("1mvr")).isFalse();
    }

    @Test
    @DisplayName("Rejects slugs with special characters or spaces")
    void rejects_special_chars() {
        assertThat(SlugValidator.isValidFormat("mvr-arg")).isFalse();
        assertThat(SlugValidator.isValidFormat("mvr.arg")).isFalse();
        assertThat(SlugValidator.isValidFormat("mvr arg")).isFalse();
        assertThat(SlugValidator.isValidFormat("mvr_arg")).isFalse();
    }

    @Test
    @DisplayName("Marks every reserved slug as reserved (case-insensitive)")
    void rejects_all_reserved() {
        String[] reserved = {
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
            "demo"
        };
        for (String slug : reserved) {
            assertThat(SlugValidator.isReserved(slug))
                    .as("'%s' should be reserved", slug)
                    .isTrue();
            assertThat(SlugValidator.isReserved(slug.toUpperCase()))
                    .as("'%s' (uppercase) should also be reserved", slug)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("isAcceptable combines format and reserved checks")
    void is_acceptable_combines_checks() {
        // Valid format but reserved → not acceptable
        assertThat(SlugValidator.isAcceptable("admin")).isFalse();
        // Valid format AND not reserved → acceptable
        assertThat(SlugValidator.isAcceptable("acme")).isTrue();
        // Invalid format → not acceptable (regardless of reserved)
        assertThat(SlugValidator.isAcceptable("Admin")).isFalse();
    }
}
