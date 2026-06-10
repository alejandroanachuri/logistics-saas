package ar.com.logistics.common.exception;

import java.util.Map;

/**
 * 409 — a uniqueness or business-rule conflict was detected. The caller
 * (typically a controller or service) maps specific {@link ErrorCode}
 * values to concrete subclasses (e.g. {@code SlugAlreadyTakenException})
 * for self-documenting call sites.
 */
public class ResourceConflictException extends BusinessException {
    public ResourceConflictException(ErrorCode code) {
        super(code);
    }

    public ResourceConflictException(ErrorCode code, Map<String, Object> details) {
        super(code, details);
    }

    public ResourceConflictException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
