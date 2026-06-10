package ar.com.logistics.common.exception;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Base class for all checked, mapped exceptions thrown by the application
 * layer. Each instance carries:
 * <ul>
 *   <li>An {@link ErrorCode} that pins the HTTP status and wire-format
 *       {@code code} string the client will see.</li>
 *   <li>An optional, immutable details map for structured field-level
 *       information (e.g. {@code {"slug":"must be lowercase"}}).</li>
 *   <li>An optional override message; if absent, the {@link ErrorCode}'s
 *       default message is used.</li>
 * </ul>
 *
 * <p>Subclasses (e.g. {@code ResourceNotFoundException}) are
 * semantic shortcuts; throwing them is equivalent to throwing
 * {@code new BusinessException(SomeCode, ...)} but with a self-documenting
 * name at the call site.
 *
 * <p>Why unchecked (extends {@link RuntimeException})? Controller
 * signatures stay clean. The {@code GlobalExceptionHandler} catches
 * the base class and maps it to a response.
 */
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> details;

    /** Use the catalog's default message and an empty details map. */
    protected BusinessException(ErrorCode code) {
        this(code, code.defaultMessage(), null);
    }

    /** Use the catalog's default message with caller-supplied details. */
    protected BusinessException(ErrorCode code, Map<String, Object> details) {
        this(code, code.defaultMessage(), details);
    }

    /** Use an explicit override message and details. */
    protected BusinessException(ErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.details = details == null ? Collections.emptyMap() : Map.copyOf(details);
    }

    /** The {@link ErrorCode} this exception maps to. Never null. */
    public ErrorCode code() {
        return code;
    }

    /**
     * Immutable view of the details map. Returns an empty map when the
     * exception was constructed without details. The map is a defensive
     * copy of the caller's input, so subsequent mutations do NOT leak in.
     */
    public Map<String, Object> details() {
        return details;
    }

    /**
     * Human-readable message sent to the client. Falls back to the
     * catalog's default message when the caller did not provide an
     * override.
     */
    public String message() {
        String m = getMessage();
        return (m == null || m.isBlank()) ? code.defaultMessage() : m;
    }
}
