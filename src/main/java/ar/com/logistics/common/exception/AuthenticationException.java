package ar.com.logistics.common.exception;

import java.util.Map;

/**
 * 401/403 — credential or scope-related failures. The constructor
 * picks the {@link ErrorCode} so the call site stays self-documenting
 * (e.g. {@code throw new AuthenticationException(INVALID_CREDENTIALS)}).
 */
public class AuthenticationException extends BusinessException {
    public AuthenticationException(ErrorCode code) {
        super(code);
    }

    public AuthenticationException(ErrorCode code, Map<String, Object> details) {
        super(code, details);
    }

    public AuthenticationException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
