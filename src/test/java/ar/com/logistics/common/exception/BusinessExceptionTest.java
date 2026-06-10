package ar.com.logistics.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the error-catalog primitives: the {@link ErrorCode} enum
 * and the abstract {@link BusinessException} base class.
 *
 * <p>These are the foundations every other piece of the error pipeline
 * (controller advice, audit log, validation) builds on. If they are wrong,
 * every error response is wrong.
 */
class BusinessExceptionTest {

    @Test
    @DisplayName("ErrorCode exposes the canonical HTTP status for each catalog entry")
    void error_code_carries_http_status() {
        assertThat(ErrorCode.VALIDATION_ERROR.httpStatus()).isEqualTo(400);
        assertThat(ErrorCode.SLUG_ALREADY_TAKEN.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.CUIT_ALREADY_REGISTERED.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.RESERVED_SLUG.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.USERNAME_ALREADY_TAKEN.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.EMAIL_ALREADY_TAKEN.httpStatus()).isEqualTo(409);
        assertThat(ErrorCode.TENANT_NOT_FOUND.httpStatus()).isEqualTo(404);
        assertThat(ErrorCode.INVALID_CREDENTIALS.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.UNAUTHENTICATED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN_SCOPE.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.ACCOUNT_LOCKED.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.ACCOUNT_DISABLED.httpStatus()).isEqualTo(403);
        assertThat(ErrorCode.REFRESH_TOKEN_INVALID.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.REFRESH_TOKEN_EXPIRED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.REFRESH_TOKEN_REVOKED.httpStatus()).isEqualTo(401);
        assertThat(ErrorCode.RATE_LIMIT_EXCEEDED.httpStatus()).isEqualTo(429);
        assertThat(ErrorCode.INTERNAL_ERROR.httpStatus()).isEqualTo(500);
    }

    @Test
    @DisplayName("ErrorCode.code() returns the canonical wire-format string")
    void error_code_returns_wire_string() {
        assertThat(ErrorCode.VALIDATION_ERROR.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(ErrorCode.SLUG_ALREADY_TAKEN.code()).isEqualTo("SLUG_ALREADY_TAKEN");
        assertThat(ErrorCode.ACCOUNT_LOCKED.code()).isEqualTo("ACCOUNT_LOCKED");
        assertThat(ErrorCode.INVALID_CREDENTIALS.code()).isEqualTo("INVALID_CREDENTIALS");
    }

    @Test
    @DisplayName("ErrorCode has a non-null, non-empty default message")
    void error_code_default_message_is_present() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage())
                    .as("defaultMessage for %s", code)
                    .isNotNull()
                    .isNotBlank();
        }
    }

    @Test
    @DisplayName("BusinessException carries its ErrorCode and an empty details map by default")
    void business_exception_defaults_to_empty_details() {
        BusinessException ex = new TestBusinessException(ErrorCode.INVALID_CREDENTIALS);
        assertThat(ex.code()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
        assertThat(ex.details()).isEmpty();
    }

    @Test
    @DisplayName("BusinessException returns a defensive copy of the details map")
    void business_exception_returns_defensive_copy_of_details() {
        Map<String, Object> input = new java.util.HashMap<>();
        input.put("slug", "mvr");
        BusinessException ex = new TestBusinessException(ErrorCode.SLUG_ALREADY_TAKEN, input);
        // mutate the source map; the exception's snapshot must not change
        input.put("slug", "tampered");
        assertThat(ex.details()).containsEntry("slug", "mvr");
    }

    @Test
    @DisplayName("BusinessException.details() rejects null in favor of an empty map")
    void business_exception_treats_null_details_as_empty() {
        BusinessException ex = new TestBusinessException(ErrorCode.SLUG_ALREADY_TAKEN, (Map<String, Object>) null);
        assertThat(ex.details()).isEmpty();
    }

    @Test
    @DisplayName("BusinessException.message() falls back to the ErrorCode default message")
    void business_exception_message_falls_back_to_default() {
        BusinessException ex = new TestBusinessException(ErrorCode.INVALID_CREDENTIALS);
        assertThat(ex.message()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.defaultMessage());
    }

    @Test
    @DisplayName("BusinessException accepts an explicit override message")
    void business_exception_accepts_override_message() {
        BusinessException ex = new TestBusinessException(
                ErrorCode.ACCOUNT_LOCKED, "Custom lockout message", Map.of("retryAfterSeconds", 900));
        assertThat(ex.message()).isEqualTo("Custom lockout message");
        assertThat(ex.details()).containsEntry("retryAfterSeconds", 900);
    }

    @Test
    @DisplayName("BusinessException is a RuntimeException so controllers can throw it without 'throws' clauses")
    void business_exception_is_a_runtime_exception() {
        assertThat(new TestBusinessException(ErrorCode.INTERNAL_ERROR))
                .as("BusinessException must be unchecked to keep controller signatures clean")
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("BusinessException is throwable from a lambda and round-trips its code")
    void business_exception_is_throwable_from_lambda() {
        assertThatThrownBy(() -> {
                    throw new TestBusinessException(ErrorCode.INTERNAL_ERROR);
                })
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * Test-only subclass of {@link BusinessException} so we can exercise the
     * abstract base class without dragging in any real exception subtype.
     */
    private static final class TestBusinessException extends BusinessException {
        TestBusinessException(ErrorCode code) {
            super(code);
        }

        TestBusinessException(ErrorCode code, Map<String, Object> details) {
            super(code, details);
        }

        TestBusinessException(ErrorCode code, String message, Map<String, Object> details) {
            super(code, message, details);
        }
    }
}
