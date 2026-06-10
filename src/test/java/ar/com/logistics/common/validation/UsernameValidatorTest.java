package ar.com.logistics.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UsernameValidator}. Format: 3-30 chars, lowercase
 * alphanum plus {@code _ . -}, first character must be a letter.
 */
class UsernameValidatorTest {

    @Test
    @DisplayName("Accepts a valid 3-character username (the minimum)")
    void accepts_minimum_length() {
        assertThat(UsernameValidator.isValid("abc")).isTrue();
    }

    @Test
    @DisplayName("Accepts a valid 30-character username (the maximum)")
    void accepts_maximum_length() {
        // Build a 30-char username: 1 leading letter + 29 alphanumeric chars
        String max = "a" + "1".repeat(29);
        assertThat(max).hasSize(30);
        assertThat(UsernameValidator.isValid(max)).isTrue();
        // 31 chars is rejected
        String tooLong = "a" + "1".repeat(30);
        assertThat(tooLong).hasSize(31);
        assertThat(UsernameValidator.isValid(tooLong)).isFalse();
    }

    @Test
    @DisplayName("Accepts usernames with the allowed separators")
    void accepts_separators() {
        assertThat(UsernameValidator.isValid("john_doe")).isTrue();
        assertThat(UsernameValidator.isValid("john.doe")).isTrue();
        assertThat(UsernameValidator.isValid("john-doe")).isTrue();
        assertThat(UsernameValidator.isValid("admin_2025")).isTrue();
    }

    @Test
    @DisplayName("Rejects null and too-short usernames")
    void rejects_too_short() {
        assertThat(UsernameValidator.isValid(null)).isFalse();
        assertThat(UsernameValidator.isValid("")).isFalse();
        assertThat(UsernameValidator.isValid("ab")).isFalse(); // 2 chars
    }

    @Test
    @DisplayName("Rejects usernames starting with a digit or separator")
    void rejects_leading_non_letter() {
        assertThat(UsernameValidator.isValid("1abc")).isFalse();
        assertThat(UsernameValidator.isValid("_abc")).isFalse();
        assertThat(UsernameValidator.isValid(".abc")).isFalse();
        assertThat(UsernameValidator.isValid("-abc")).isFalse();
    }

    @Test
    @DisplayName("Rejects usernames with uppercase or non-ASCII characters")
    void rejects_uppercase_and_non_ascii() {
        assertThat(UsernameValidator.isValid("Admin")).isFalse();
        assertThat(UsernameValidator.isValid("adMin")).isFalse();
        assertThat(UsernameValidator.isValid("john_doe@")).isFalse();
        assertThat(UsernameValidator.isValid("john doe")).isFalse();
    }
}
