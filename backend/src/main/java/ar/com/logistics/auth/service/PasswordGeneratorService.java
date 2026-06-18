package ar.com.logistics.auth.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Generates strong, single-use passwords for the
 * {@code CompanyUsersService.create} (manual flow) and
 * {@code CompanyUsersService.resetPassword} (auto-generated) endpoints.
 *
 * <p>Spec C3 mandates:
 * <ul>
 *   <li>Length ≥ 12 characters.</li>
 *   <li>At least one uppercase letter, one lowercase letter, one digit,
 *       AND one symbol from the {@code !@#$%^&*} pool.</li>
 *   <li>Driven by {@link SecureRandom} (NEVER {@link java.util.Random} or
 *       {@link Math#random()}).</li>
 * </ul>
 *
 * <p>Implementation strategy (avoids positional predictability): draw
 * one character from each of the four buckets first to guarantee
 * coverage, fill the remaining positions from the full pool, then
 * shuffle the result with the same {@link SecureRandom} instance.
 *
 * <p>Single-use discipline: the caller MUST NOT log the returned
 * password or store it anywhere except (a) the HTTP response and
 * (b) the BCrypt-hashed value on the {@code company_users} row.
 * The wrapper record {@link GeneratedPassword} keeps the value
 * behind a single accessor so accidental string interpolation in
 * log statements is harder to do by mistake.
 */
@Component
public class PasswordGeneratorService {

    /** Pool of symbols allowed in a generated password. Per spec C3. */
    static final String SYMBOL_POOL = "!@#$%^&*";

    /** Lower bound on generated length. Per spec C3. */
    static final int MIN_LENGTH = 12;

    /** The RNG is package-private so the unit test can assert the type. */
    final SecureRandom random;

    public PasswordGeneratorService() {
        this(new SecureRandom());
    }

    /**
     * Constructor accepting an injected {@link SecureRandom} for test
     * determinism. Production wiring uses the no-arg constructor; tests
     * can pass a seeded RNG to pin entropy if needed (not currently
     * exercised — the unit test only asserts the runtime type).
     */
    PasswordGeneratorService(SecureRandom random) {
        this.random = random;
    }

    /** Generate a password of length {@value #MIN_LENGTH} or more. */
    public GeneratedPassword generate() {
        return generate(MIN_LENGTH);
    }

    /**
     * Generate a password of at least {@code minLength} characters.
     * Throws {@link IllegalArgumentException} when {@code minLength}
     * is below {@value #MIN_LENGTH} — the policy floor is enforced
     * here, not in the controller, so it cannot be bypassed.
     */
    public GeneratedPassword generate(int minLength) {
        if (minLength < MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "minLength must be at least " + MIN_LENGTH + " (spec C3); got " + minLength);
        }

        List<Character> chars = new ArrayList<>(minLength);

        // 1. Guarantee one char from each bucket — spec C3.
        chars.add(randomUpper());
        chars.add(randomLower());
        chars.add(randomDigit());
        chars.add(randomSymbol());

        // 2. Fill the remaining positions from the full pool.
        String fullPool = UPPER_POOL + LOWER_POOL + DIGIT_POOL + SYMBOL_POOL;
        for (int i = 4; i < minLength; i++) {
            chars.add(fullPool.charAt(random.nextInt(fullPool.length())));
        }

        // 3. Shuffle so the four "guarantee" positions are not
        //    predictable (defeats a v2 attacker who knows our
        //    strategy).
        Collections.shuffle(chars, random);

        char[] out = new char[chars.size()];
        for (int i = 0; i < chars.size(); i++) {
            out[i] = chars.get(i);
        }
        return new GeneratedPassword(new String(out));
    }

    private static final String UPPER_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_POOL = "abcdefghijklmnopqrstuvwxyz";
    private static final String DIGIT_POOL = "0123456789";

    private char randomUpper() {
        return UPPER_POOL.charAt(random.nextInt(UPPER_POOL.length()));
    }

    private char randomLower() {
        return LOWER_POOL.charAt(random.nextInt(LOWER_POOL.length()));
    }

    private char randomDigit() {
        return DIGIT_POOL.charAt(random.nextInt(DIGIT_POOL.length()));
    }

    private char randomSymbol() {
        return SYMBOL_POOL.charAt(random.nextInt(SYMBOL_POOL.length()));
    }

    /**
     * Read-only wrapper around a generated password. The single accessor
     * is intentional — the caller MUST pass this object to the HTTP
     * response builder and then drop it; BCrypt-hashing happens
     * separately. Avoids the "string in a log message" footgun where
     * an interceptor accidentally serialises the field.
     */
    public record GeneratedPassword(String value) {}
}
