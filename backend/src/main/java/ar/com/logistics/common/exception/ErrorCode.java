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
    INTERNAL_ERROR(500, "INTERNAL_ERROR", "Internal server error."),

    // -- Etapa 2: team management (PR-3 controllers) ---------------

    /** 404 — company user not found (or belongs to another tenant). */
    USER_NOT_FOUND(404, "USER_NOT_FOUND", "User not found."),

    /** 400 — one or more role ids in the request are invalid (PLATFORM scope or non-existent). */
    INVALID_ROLE(400, "INVALID_ROLE", "One or more role ids are invalid."),

    /** 409 — user is already DISABLED. */
    USER_ALREADY_DISABLED(409, "USER_ALREADY_DISABLED", "User is already disabled."),

    /** 409 — user is already ACTIVE. */
    USER_ALREADY_ACTIVE(409, "USER_ALREADY_ACTIVE", "User is already active."),

    /** 403 — admin attempted to PATCH their own user row. */
    SELF_EDIT_BLOCKED(403, "SELF_EDIT_BLOCKED", "You cannot edit your own user from this endpoint."),

    /** 403 — admin attempted to disable their own user row. */
    SELF_DISABLE_BLOCKED(403, "SELF_DISABLE_BLOCKED", "You cannot disable your own user."),

    /** 403 — first admin of the tenant is protected from this mutation (email/username/role immutable). */
    FIRST_ADMIN_PROTECTED(403, "FIRST_ADMIN_PROTECTED", "The first admin of the tenant is protected from this action."),

    /** 403 — the tenant must always retain at least one active COMPANY_ADMIN. */
    LAST_ADMIN_PROTECTED(403, "LAST_ADMIN_PROTECTED", "The tenant must always have at least one active COMPANY_ADMIN."),

    // -- Etapa 3: shipment lifecycle (PR-2 controllers / PR-3 services) -----

    /** 422 — FSM transition not allowed by the immutable VALID_TRANSITIONS map. */
    INVALID_STATE_TRANSITION(
            422, "INVALID_STATE_TRANSITION", "The requested package state transition is not permitted by the FSM."),

    /** 409 — same tracking_events.event_hash already present; event is idempotent. */
    DUPLICATE_EVENT(409, "DUPLICATE_EVENT", "This event has already been registered (idempotency hash collision)."),

    /** 403 — the actor's role is not authorized to emit the given event_type. */
    INSUFFICIENT_PERMISSIONS(403, "INSUFFICIENT_PERMISSIONS", "Your role is not authorized to emit this event."),

    /** 422 — sender_id and receiver_id are equal. */
    SENDER_EQUALS_RECEIVER(422, "SENDER_EQUALS_RECEIVER", "Sender and receiver must be different customers."),

    /** 400 — DNI failed the 7-8 digit format check. */
    DNI_INVALID(400, "DNI_INVALID", "DNI must be 7 or 8 digits."),

    /** 400 — CUIT failed the modulo-11 verifier check. */
    CUIT_INVALID(400, "CUIT_INVALID", "CUIT check digit does not match."),

    /** 409 — a customer with the same DNI already exists in this tenant. */
    DNI_ALREADY_EXISTS(409, "DNI_ALREADY_EXISTS", "A customer with this DNI already exists in your company."),

    /** 409 — a customer with the same CUIT already exists in this tenant. */
    CUIT_ALREADY_EXISTS(409, "CUIT_ALREADY_EXISTS", "A customer with this CUIT already exists in your company."),

    /** 404 — customer not found (or belongs to another tenant). */
    CUSTOMER_NOT_FOUND(404, "CUSTOMER_NOT_FOUND", "Customer not found."),

    /** 404 — address not found. */
    ADDRESS_NOT_FOUND(404, "ADDRESS_NOT_FOUND", "Address not found."),

    /** 404 — package not found. */
    PACKAGE_NOT_FOUND(404, "PACKAGE_NOT_FOUND", "Package not found."),

    /** 404 — shipment not found. */
    SHIPMENT_NOT_FOUND(404, "SHIPMENT_NOT_FOUND", "Shipment not found."),

    /** 404 — branch not found. */
    BRANCH_NOT_FOUND(404, "BRANCH_NOT_FOUND", "Branch not found."),

    /** 404 — service_level not found. */
    SERVICE_LEVEL_NOT_FOUND(404, "SERVICE_LEVEL_NOT_FOUND", "Service level not found."),

    /** 404 — public tracking id does not match any shipment. */
    TRACKING_NOT_FOUND(404, "TRACKING_NOT_FOUND", "Tracking id not found."),

    /** 403 — tenant's status field is SUSPENDED; cannot create shipments. */
    COMPANY_SUSPENDED(403, "COMPANY_SUSPENDED", "Your company is suspended and cannot create shipments right now."),

    /** 400 — event contract violation (missing mandatory fields per event_type schema). */
    EVENT_VALIDATION_ERROR(400, "EVENT_VALIDATION_ERROR", "The event payload is missing required fields for its type."),

    /** 422 — data_consent was true but consent_date is null (or vice versa, depending on direction). */
    NO_DATA_CONSENT(422, "NO_DATA_CONSENT", "Data consent must be acknowledged with a consent date.");

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
