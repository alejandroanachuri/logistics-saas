package ar.com.logistics.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Wire-format envelope for every error response.
 *
 * <p>JSON shape (PRD line 505):
 * <pre>
 * { "error": { "code": "...", "message": "...", "details": { ... } } }
 * </pre>
 *
 * <p>The {@code details} map is omitted from the JSON entirely when
 * the originating exception has no structured fields (e.g.
 * {@code INVALID_CREDENTIALS}) so the response stays clean.
 */
public record ErrorEnvelope(@JsonProperty("error") ErrorBody error) {

    public ErrorEnvelope {
        if (error == null) {
            throw new IllegalArgumentException("error body is required");
        }
    }

    /**
     * Convenience factory that builds the envelope from a
     * {@link BusinessException} (uses the exception's code, message,
     * and details).
     */
    public static ErrorEnvelope from(BusinessException ex) {
        Map<String, Object> details = ex.details();
        if (details == null || details.isEmpty()) {
            return new ErrorEnvelope(new ErrorBody(ex.code().code(), ex.message(), null));
        }
        return new ErrorEnvelope(new ErrorBody(ex.code().code(), ex.message(), details));
    }

    /**
     * Build an envelope from an {@link ErrorCode} + message + optional
     * details. Used by the controller advice for non-{@code BusinessException}
     * cases (Bean Validation, malformed JSON, generic fallback).
     */
    public static ErrorEnvelope of(ErrorCode code, String message, Map<String, Object> details) {
        return new ErrorEnvelope(new ErrorBody(code.code(), message, details));
    }

    /** The inner body record. {@code details} is hidden from JSON when null or empty. */
    public record ErrorBody(
            String code, String message, @JsonInclude(JsonInclude.Include.NON_EMPTY) Map<String, Object> details) {}
}
