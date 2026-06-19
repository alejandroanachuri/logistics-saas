package ar.com.logistics.common.exception;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised mapping from application / Spring exceptions to
 * {@link ErrorEnvelope} responses.
 *
 * <p>Four responsibilities:
 * <ol>
 *   <li>Turn {@link BusinessException} (and subclasses) into a
 *       response with the correct HTTP status and envelope body.</li>
 *   <li>Turn {@link BusinessRuleException} (the service-layer
 *       placeholder that carries a {@code String code} instead of an
 *       {@link ErrorCode}) into a response by looking the code up in
 *       the {@link ErrorCode} catalog. Falls back to 500 on an
 *       unknown code (defensive — should never happen).</li>
 *   <li>Map Bean-Validation failures ({@link MethodArgumentNotValidException})
 *       to {@code 400 VALIDATION_ERROR} with a per-field details map.</li>
 *   <li>Catch-all {@link Exception} → {@code 500 INTERNAL_ERROR}. The
 *       message is generic; the actual stack trace is logged by the
 *       application logger (Sentry is the prod sink, design §11).</li>
 * </ol>
 *
 * <p>Side-channel: {@code ACCOUNT_LOCKED} responses get a
 * {@code Retry-After} header in addition to the
 * {@code details.retryAfterSeconds} field, per spec-patch 0.3.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorEnvelope> handleBusinessException(BusinessException ex) {
        HttpStatus status = HttpStatus.valueOf(ex.code().httpStatus());
        ErrorEnvelope body = ErrorEnvelope.from(ex);
        HttpHeaders headers = new HttpHeaders();
        if (ex.code() == ErrorCode.ACCOUNT_LOCKED) {
            Object retryAfter = ex.details().get("retryAfterSeconds");
            if (retryAfter instanceof Number n) {
                headers.add(HttpHeaders.RETRY_AFTER, Long.toString(n.longValue()));
            }
        }
        return new ResponseEntity<>(body, headers, status);
    }

    /**
     * Map {@link BusinessRuleException} (the service-layer placeholder
     * that carries a {@code String code} — see {@code BusinessRuleException}
     * javadoc) to the canonical {@link ErrorEnvelope} response. The
     * lookup strategy uses {@link ErrorCode#valueOf(String)} on the
     * canonical wire-format code, so the service-layer and the catalog
     * must agree on the spelling.
     *
     * <p>On an unknown code (which is a service-layer bug — every code
     * MUST be in the catalog) the handler falls back to
     * {@code 500 INTERNAL_ERROR} with the raw code embedded in the
     * message so the failure is loud in the audit log rather than
     * crashing the handler with an {@link IllegalArgumentException}.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ErrorEnvelope> handleBusinessRule(BusinessRuleException ex) {
        ErrorCode mapped;
        try {
            mapped = ErrorCode.valueOf(ex.code());
        } catch (IllegalArgumentException unknownCode) {
            // Defensive — the contract is "every code is in the catalog".
            // Don't propagate the IAE (that would surface as a 500 stack
            // trace to the client); instead return a sanitized 500 with
            // the unknown code visible in the message for debugging.
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorEnvelope(new ErrorEnvelope.ErrorBody(
                            ErrorCode.INTERNAL_ERROR.code(),
                            "Unmapped BusinessRuleException code: " + ex.code(),
                            ex.details())));
        }
        HttpStatus status = HttpStatus.valueOf(mapped.httpStatus());
        ErrorEnvelope body =
                new ErrorEnvelope(new ErrorEnvelope.ErrorBody(mapped.code(), ex.getMessage(), ex.details()));
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Map Bean-Validation failures ({@code @Valid} on {@code @RequestBody})
     * to a 400 with a per-field details map. The field name is the
     * property path, the value is the default human-readable message
     * (one entry per failing constraint on each field).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(err -> {
            String key = err.getField();
            String msg = err.getDefaultMessage() == null ? "invalid" : err.getDefaultMessage();
            details.merge(key, msg, (a, b) -> a + "; " + b);
        });
        ErrorEnvelope body =
                ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultMessage(), details);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Malformed JSON in the request body — keep the wire-format
     * consistent with other validation failures.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMalformedJson(HttpMessageNotReadableException ex) {
        ErrorEnvelope body = ErrorEnvelope.of(ErrorCode.VALIDATION_ERROR, "Malformed JSON request body.", null);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * Fallback. We log the exception with the application logger at
     * ERROR (Sentry picks it up in prod) and return a generic 500
     * envelope so the client never sees stack traces or internal
     * details.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
        org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class)
                .error("Unhandled exception bubbled to the controller advice", ex);
        ErrorEnvelope body =
                ErrorEnvelope.of(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage(), null);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
