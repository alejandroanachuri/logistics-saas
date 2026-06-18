package ar.com.logistics.common.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Placeholder exception for the PR-2 service layer. Carries an opaque
 * {@code code} string so the service layer can throw business-rule
 * violations ({@code SELF_EDIT_BLOCKED}, {@code FIRST_ADMIN_PROTECTED},
 * {@code LAST_ADMIN_PROTECTED}, etc.) WITHOUT depending on the
 * {@link ErrorCode} catalog yet — PR-3 will (a) add the 8 new codes to
 * {@link ErrorCode} and (b) wire {@link GlobalExceptionHandler} to map
 * {@code BusinessRuleException} → {@code ErrorEnvelope} with the
 * appropriate HTTP status.
 *
 * <p>Why a string code and not the {@link ErrorCode} enum? Because PR-3
 * is the canonical home for those codes, and the apply batch for PR-3
 * will own the ErrorCode + GlobalExceptionHandler wiring. PR-2 only
 * needs the throw-site contract to be stable so the controller can be
 * added without restructuring the services.
 *
 * <p>Throws are unchecked ({@link RuntimeException}) so service
 * signatures stay clean. Constructor accepts the {@code code} string
 * (canonical wire-format) and an optional details map for
 * field-level information (e.g. {@code {"roleIds": "[uuid1, uuid2]"}}).
 */
public class BusinessRuleException extends RuntimeException {

    private final String code;
    private final Map<String, Object> details;

    /** Use the catalog's default message and an empty details map. */
    public BusinessRuleException(String code) {
        this(code, code, null);
    }

    /** Use the catalog's default message with caller-supplied details. */
    public BusinessRuleException(String code, Map<String, Object> details) {
        this(code, code, details);
    }

    /** Use an explicit override message and details. */
    public BusinessRuleException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** The canonical wire-format code string the client will see in PR-3. */
    public String code() {
        return code;
    }

    /**
     * Immutable view of the details map. Empty when the exception was
     * constructed without details. Defensive copy of the caller's input.
     */
    public Map<String, Object> details() {
        return details;
    }

    @Override
    public String getMessage() {
        return super.getMessage() == null ? code : super.getMessage();
    }
}
