package ar.com.logistics.shipment.tracking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LgstGeneratorService}. The service is
 * Spring-annotated but has a public constructor — instantiate it
 * directly to test the retry loop without DI.
 */
class LgstGeneratorServiceTest {

    private static final String VALID_REGEX = "^LGST-[0-9A-HJKMNP-TV-Z]{8}$";

    // ------------------------------------------------------------------------
    // Happy path.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Returns the generated id when the predicate accepts on the first attempt")
    void first_attempt_success() {
        LgstGeneratorService service = new LgstGeneratorService(5);
        AtomicInteger callCount = new AtomicInteger();
        List<String> seen = new ArrayList<>();

        String result = service.generateAndPersist(candidate -> {
            callCount.incrementAndGet();
            seen.add(candidate);
            return true; // always accept
        });

        assertThat(callCount.get()).isEqualTo(1);
        assertThat(seen).hasSize(1);
        assertThat(result).isEqualTo(seen.get(0));
        assertThat(result).matches(VALID_REGEX);
    }

    // ------------------------------------------------------------------------
    // Retry success.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Retries until the predicate accepts and returns the accepted id")
    void retry_then_success() {
        LgstGeneratorService service = new LgstGeneratorService(5);
        AtomicInteger callCount = new AtomicInteger();
        List<String> seen = new ArrayList<>();

        String result = service.generateAndPersist(candidate -> {
            callCount.incrementAndGet();
            seen.add(candidate);
            // Reject the first two, accept the third.
            return callCount.get() >= 3;
        });

        assertThat(callCount.get()).isEqualTo(3);
        assertThat(seen).hasSize(3);
        assertThat(result).isEqualTo(seen.get(2));
        assertThat(result).matches(VALID_REGEX);
        // All three candidates are valid LGST-XXXXXXXX strings.
        assertThat(seen).allMatch(s -> s.matches(VALID_REGEX));
    }

    // ------------------------------------------------------------------------
    // Exhaustion.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Throws IllegalStateException after maxAttempts collisions")
    void exhaustion_throws_illegal_state() {
        LgstGeneratorService service = new LgstGeneratorService(5);
        AtomicInteger callCount = new AtomicInteger();

        assertThatThrownBy(() -> service.generateAndPersist(candidate -> {
                    callCount.incrementAndGet();
                    return false; // never accept
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("5 attempts");

        assertThat(callCount.get()).isEqualTo(5);
    }

    // ------------------------------------------------------------------------
    // Custom maxAttempts.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Honors a custom maxAttempts value (e.g. 2)")
    void honors_custom_max_attempts() {
        LgstGeneratorService service = new LgstGeneratorService(2);
        AtomicInteger callCount = new AtomicInteger();

        assertThatThrownBy(() -> service.generateAndPersist(candidate -> {
                    callCount.incrementAndGet();
                    return false;
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("2 attempts");

        assertThat(callCount.get()).isEqualTo(2);
    }

    // ------------------------------------------------------------------------
    // Output validity — the returned id is always a valid LGST id.
    // ------------------------------------------------------------------------

    @Test
    @DisplayName("Returned id always matches the LgstGenerator canonical regex")
    void returned_id_is_always_valid() {
        LgstGeneratorService service = new LgstGeneratorService(10);

        // Force several retries to exercise the loop, but accept on the
        // last attempt so the call returns. The returned id must be valid.
        for (int trial = 0; trial < 20; trial++) {
            AtomicInteger attempts = new AtomicInteger();
            String result = service.generateAndPersist(candidate -> {
                int n = attempts.incrementAndGet();
                // Accept every other call so we get both retries and successes.
                return n % 2 == 0;
            });
            assertThat(result).matches(VALID_REGEX);
            assertThat(LgstGenerator.isValid(result)).isTrue();
        }
    }
}
