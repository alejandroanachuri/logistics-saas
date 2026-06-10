package ar.com.logistics.common.exception;

import java.util.Map;

/**
 * Canonical catalog of error codes that the API can return.
 *
 * <p>Each entry pins:
 * <ul>
 *   <li>The HTTP status the controller advice will use.</li>
 *   <li>The {@code code} string the client sees in the JSON envelope.</li>
 *   <li>A default human-readable message (overridable per call).</li>
 * </ul>
 *
 * <p>New codes must be added here, not invented at the call site, so the
 * contract surface stays small and discoverable. The list mirrors
 * {@code design.md} §6.
 */
public enum ErrorCode {
    /** 400 — body / field-level validation failures. */
    VALIDATION_ERROR(400, "VALIDATION_ERROR", "Validation failed for one or more fields."),

    /** 409 — slug uniqueness violation. */
    SLUG_ALREADY_TAKEN(409, "SLUG_ALREADY_TAKEN", "Slug is already in use."),

    /** 409 — CUIT uniqueness violation. */
    CUIT_ALREADY_REGISTERED(409, "CUIT_ALREADY_REGISTERED", "CUIT is already registered."),

    /** 409 — slug is in the reserved catalog. */
    RESERVED_SLUG(409, "RESERVED_SLUG", "Slug is reserved and cannot be used."),

    /** 409 — username uniqueness violation (per tenant). */
    USERNAME_ALREADY_TAKEN(409, "USERNAME_ALREADY_TAKEN", "Username is already in use."),

    /** 409 — email uniqueness violation. */
    EMAIL_ALREADY_TAKEN(409, "EMAIL_ALREADY_TAKEN", "Email is already in use."),

    /** 404 — tenant not found (placeholder, exercised by platform endpoints in PR8). */
    TENANT_NOT_FOUND(404, "TENANT_NOT_FOUND", "Tenant not found."),

    /** 401 — invalid credentials (login path). */
    INVALID_CREDENTIALS(401, "INVALID_CREDENTIALS", "Invalid credentials."),

    /** 401 — missing or invalid access token on an authenticated path. */
    UNAUTHENTICATED(401, "UNAUTHENTICATED", "Authentication is required to access this resource."),

    /** 403 — JWT scope does not match the request path prefix. */
    FORBIDDEN_SCOPE(403, "FORBIDDEN_SCOPE", "The presented scope is not authorized for this resource."),

    /** 403 — account is currently locked; details include {@code retryAfterSeconds}. */
    ACCOUNT_LOCKED(403, "ACCOUNT_LOCKED", "Account is locked. Try again later."),

    /** 403 — account exists but is administratively disabled. */
    ACCOUNT_DISABLED(403, "ACCOUNT_DISABLED", "Account is disabled."),

    /** 401 — refresh token cookie is unknown / BCrypt does not match. */
    REFRESH_TOKEN_INVALID(401, "REFRESH_TOKEN_INVALID", "Refresh token is invalid."),

    /** 401 — refresh token cookie is past its {@code expires_at}. */
    REFRESH_TOKEN_EXPIRED(401, "REFRESH_TOKEN_EXPIRED", "Refresh token has expired."),

    /** 401 — refresh token was already rotated/revoked (reuse detection). */
    REFRESH_TOKEN_REVOKED(401, "REFRESH_TOKEN_REVOKED", "Refresh token has been revoked."),

    /** 429 — Bucket4j budget exhausted for this IP + endpoint. */
    RATE_LIMIT_EXCEEDED(429, "RATE_LIMIT_EXCEEDED", "Rate limit exceeded. Try again later."),

    /** 500 — unhandled server-side failure. */
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal server error.");

    private final int httpStatus;
    private final String code;
    private final String defaultMessage;

    ErrorCode(int httpStatus, String code, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    /** HTTP status the {@code GlobalExceptionHandler} returns for this code. */
    public int httpStatus() {
        return httpStatus;
    }

    /** Wire-format string the client sees in the {@code error.code} field. */
    public String code() {
        return code;
    }

    /** Human-readable fallback message; overridable per {@code BusinessException} instance. */
    public String defaultMessage() {
        return defaultMessage;
    }

    /**
     * Convenience: pull a single detail value by key, returning {@code null}
     * if missing. Used by the controller advice to extract
     * {@code retryAfterSeconds} from an {@code ACCOUNT_LOCKED} payload
     * without leaking the full details map to the response.
     */
    public static Object detail(Map<String, Object> details, String key) {
        return details == null ? null : details.get(key);
    }
}
