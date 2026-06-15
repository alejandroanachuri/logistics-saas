package ar.com.logistics.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Raw smoke test for the Bucket4j wrapping. Proves the
 * documented budgets are consumable, that the probe reports
 * remaining tokens, and that the 429-equivalent
 * ({@code ConsumptionProbe.isConsumed() == false}) fires at the
 * right boundary.
 *
 * <p>No Spring context — the rate-limit filter is a thin wrapper
 * around Bucket4j and the bucket behaviour is the part worth
 * pinning. The 75/75 gate test
 * ({@code RegistrationIT} + {@code DswiringIT} +
 * {@code RlsIntegrationIT}) keeps the wiring honest end-to-end.
 */
class RateLimitIT {

    @Test
    @DisplayName("register bucket (5/hour) consumes 5 and rejects the 6th")
    void register_bucket_exhausts_after_five() {
        Bucket bucket = newBucket(5, Duration.ofHours(1));
        for (int i = 0; i < 5; i++) {
            assertThat(bucket.tryConsume(1))
                    .as("attempt %d should succeed", i + 1)
                    .isTrue();
        }
        assertThat(bucket.tryConsume(1)).as("6th attempt must be rejected").isFalse();
    }

    @Test
    @DisplayName("login bucket (10/min) rejects the 11th")
    void login_bucket_exhausts_after_ten() {
        Bucket bucket = newBucket(10, Duration.ofMinutes(1));
        for (int i = 0; i < 10; i++) {
            assertThat(bucket.tryConsume(1)).isTrue();
        }
        assertThat(bucket.tryConsume(1)).isFalse();
    }

    @Test
    @DisplayName("probe reports remaining tokens before exhaustion")
    void probe_reports_remaining_tokens() {
        Bucket bucket = newBucket(3, Duration.ofMinutes(1));
        assertThat(bucket.tryConsumeAndReturnRemaining(1).getRemainingTokens()).isEqualTo(2);
        assertThat(bucket.tryConsumeAndReturnRemaining(1).getRemainingTokens()).isEqualTo(1);
        assertThat(bucket.tryConsumeAndReturnRemaining(1).getRemainingTokens()).isEqualTo(0);
        assertThat(bucket.tryConsumeAndReturnRemaining(1).isConsumed()).isFalse();
    }

    private static Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }
}
