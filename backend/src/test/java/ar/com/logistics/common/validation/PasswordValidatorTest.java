package ar.com.logistics.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PasswordValidator}. v1 policy: ≥8 chars,
 * at least one uppercase, one lowercase, one digit.
 */
class PasswordValidatorTest {

    @Test
    @DisplayName("Accepts a password that meets all four rules")
    void accepts_valid_password() {
        assertThat(PasswordValidator.isValid("MiPassw0rd!Seguro")).isTrue();
        assertThat(PasswordValidator.isValid("Abcdefg1")).isTrue(); // 8 chars exactly
        assertThat(PasswordValidator.isValid("P4ssword")).isTrue();
    }

    @Test
    @DisplayName("Rejects null and empty strings")
    void rejects_null_and_empty() {
        assertThat(PasswordValidator.isValid(null)).isFalse();
        assertThat(PasswordValidator.isValid("")).isFalse();
    }

    @Test
    @DisplayName("Rejects passwords shorter than 8 characters")
    void rejects_too_short() {
        assertThat(PasswordValidator.isValid("Aa1!")).isFalse(); // 4 chars
        assertThat(PasswordValidator.isValid("Abcd1!")).isFalse(); // 6 chars
        assertThat(PasswordValidator.isValid("Abcde1!")).isFalse(); // 7 chars
    }

    @Test
    @DisplayName("Rejects passwords missing an uppercase letter")
    void rejects_no_uppercase() {
        assertThat(PasswordValidator.isValid("mypassw0rd!seguro")).isFalse();
        assertThat(PasswordValidator.isValid("abc12345")).isFalse();
    }

    @Test
    @DisplayName("Rejects passwords missing a lowercase letter")
    void rejects_no_lowercase() {
        assertThat(PasswordValidator.isValid("MYPASSW0RD!SEGURO")).isFalse();
        assertThat(PasswordValidator.isValid("ABC12345")).isFalse();
    }

    @Test
    @DisplayName("Rejects passwords missing a digit")
    void rejects_no_digit() {
        assertThat(PasswordValidator.isValid("MyPassword!Secure")).isFalse();
        assertThat(PasswordValidator.isValid("Abcdefgh!")).isFalse();
    }
}
