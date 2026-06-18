package ar.com.logistics.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.logistics.auth.service.PasswordGeneratorService.GeneratedPassword;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PasswordGeneratorService}. Strict TDD — these tests
 * define the contract for generated passwords before the implementation
 * exists.
 *
 * <p>Contract (per spec C3 + design §3.2):
 * <ul>
 *   <li>Default length ≥ 12.</li>
 *   <li>Each generated password contains at least one upper, one lower,
 *       one digit, and one symbol from {@code !@#$%^&*}.</li>
 *   <li>The generator uses {@link java.security.SecureRandom} (NEVER
 *       {@link java.util.Random} or {@link Math#random()}).</li>
 *   <li>100 consecutive generations must all be unique (sanity check
 *       that the entropy source is real, not seeded with a constant).</li>
 *   <li>Honours a caller-supplied {@code minLength} (must be ≥ 12).</li>
 * </ul>
 */
class PasswordGeneratorServiceTest {

    private final PasswordGeneratorService generator = new PasswordGeneratorService();

    @Test
    @DisplayName("Default generate() returns a password of at least 12 characters")
    void default_length_is_at_least_12() {
        GeneratedPassword p = generator.generate();
        assertThat(p.value()).as("default length must be ≥ 12").hasSizeGreaterThanOrEqualTo(12);
    }

    @Test
    @DisplayName("Generated password contains all four character classes")
    void contains_upper_lower_digit_symbol() {
        GeneratedPassword p = generator.generate();
        String v = p.value();
        assertThat(v).matches(".*[A-Z].*");
        assertThat(v).matches(".*[a-z].*");
        assertThat(v).matches(".*[0-9].*");
        assertThat(v).matches(".*[!@#$%^&*].*");
    }

    @Test
    @DisplayName("Generated password honours a caller-supplied minLength (≥ 12)")
    void honours_caller_min_length() {
        GeneratedPassword p = generator.generate(20);
        assertThat(p.value()).hasSizeGreaterThanOrEqualTo(20);
    }

    @Test
    @DisplayName("100 consecutive generations are all unique (entropy check)")
    void hundred_generations_are_unique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(generator.generate().value());
        }
        assertThat(seen).as("100 random passwords must all be distinct").hasSize(100);
    }

    @Test
    @DisplayName("Uses SecureRandom internally (not java.util.Random)")
    void uses_secure_random_not_plain_random() throws Exception {
        Field f = PasswordGeneratorService.class.getDeclaredField("random");
        f.setAccessible(true);
        Object rng = f.get(generator);
        assertThat(rng)
                .as("generator must use java.security.SecureRandom, not java.util.Random")
                .isInstanceOf(java.security.SecureRandom.class);
    }
}
