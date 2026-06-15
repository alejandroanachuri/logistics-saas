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
 * <p>Three responsibilities:
 * <ol>
 *   <li>Turn {@link BusinessException} (and subclasses) into a
 *       response with the correct HTTP status and envelope body.</li>
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
