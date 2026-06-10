package ar.com.logistics.common.exception;

import java.util.Map;

/**
 * 400 — request body failed validation. Carries a per-field
 * {@code details} map (e.g. {@code {"slug": "must be lowercase"}})
 * so the client can render field-level errors.
 */
public class ValidationException extends BusinessException {
    public ValidationException(Map<String, Object> details) {
        super(ErrorCode.VALIDATION_ERROR, details);
    }

    public ValidationException(String message, Map<String, Object> details) {
        super(ErrorCode.VALIDATION_ERROR, message, details);
    }
}
