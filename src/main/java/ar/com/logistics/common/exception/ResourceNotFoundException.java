package ar.com.logistics.common.exception;

import java.util.Map;

/**
 * 404 — the addressed resource does not exist (or is not visible under RLS).
 *
 * <p>Used by the platform side (PR8) for {@code /api/v1/platform/tenants/{id}}
 * and reserved here so the catalog stays symmetric; the company side does
 * not need a public "tenant not found" path because the tenant identity
 * is derived from the JWT.
 */
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(ErrorCode code) {
        super(code);
    }

    public ResourceNotFoundException(ErrorCode code, Map<String, Object> details) {
        super(code, details);
    }

    public ResourceNotFoundException(ErrorCode code, String message, Map<String, Object> details) {
        super(code, message, details);
    }
}
