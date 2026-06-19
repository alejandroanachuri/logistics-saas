package ar.com.logistics.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for the {@code BusinessRuleException → ErrorEnvelope}
 * handler added in etapa-2 PR-3. The handler is the bridge between
 * the service layer's opaque {@code String code} and the controller
 * advice's typed {@link ErrorCode} catalog — without it the
 * exception bubbles up as a generic 500.
 *
 * <p>The lookup strategy uses {@link ErrorCode#valueOf(String)} on the
 * canonical wire-format code (so the contract is "use the same
 * string on both sides"). When the service throws a code that does
 * NOT appear in the catalog (defensive — should never happen) the
 * handler falls back to 500 INTERNAL_ERROR with the raw code as the
 * message so the failure is loud in the audit log.
 */
class GlobalExceptionHandlerBusinessRuleTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("USER_NOT_FOUND maps to 404 with the canonical envelope shape")
    void userNotFound_mapsTo404() {
        BusinessRuleException ex =
                new BusinessRuleException("USER_NOT_FOUND", "user does not exist", Map.of("userId", "abc-123"));

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().error().code()).isEqualTo("USER_NOT_FOUND");
        assertThat(resp.getBody().error().message()).isEqualTo("user does not exist");
        assertThat(resp.getBody().error().details()).containsEntry("userId", "abc-123");
    }

    @Test
    @DisplayName("SELF_EDIT_BLOCKED maps to 403")
    void selfEditBlocked_mapsTo403() {
        BusinessRuleException ex = new BusinessRuleException("SELF_EDIT_BLOCKED");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().error().code()).isEqualTo("SELF_EDIT_BLOCKED");
    }

    @Test
    @DisplayName("FIRST_ADMIN_PROTECTED maps to 403")
    void firstAdminProtected_mapsTo403() {
        BusinessRuleException ex = new BusinessRuleException("FIRST_ADMIN_PROTECTED");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().error().code()).isEqualTo("FIRST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("LAST_ADMIN_PROTECTED maps to 403")
    void lastAdminProtected_mapsTo403() {
        BusinessRuleException ex = new BusinessRuleException("LAST_ADMIN_PROTECTED");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().error().code()).isEqualTo("LAST_ADMIN_PROTECTED");
    }

    @Test
    @DisplayName("INVALID_ROLE maps to 400 and preserves details.invalidRoleIds")
    void invalidRole_mapsTo400WithDetails() {
        BusinessRuleException ex = new BusinessRuleException(
                "INVALID_ROLE",
                "One or more role ids are invalid.",
                Map.of("invalidRoleIds", java.util.List.of("uuid-1", "uuid-2")));

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(resp.getBody().error().code()).isEqualTo("INVALID_ROLE");
        assertThat(resp.getBody().error().details()).containsKey("invalidRoleIds");
    }

    @Test
    @DisplayName("USER_ALREADY_DISABLED maps to 409")
    void userAlreadyDisabled_mapsTo409() {
        BusinessRuleException ex = new BusinessRuleException("USER_ALREADY_DISABLED");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error().code()).isEqualTo("USER_ALREADY_DISABLED");
    }

    @Test
    @DisplayName("USER_ALREADY_ACTIVE maps to 409")
    void userAlreadyActive_mapsTo409() {
        BusinessRuleException ex = new BusinessRuleException("USER_ALREADY_ACTIVE");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(resp.getBody().error().code()).isEqualTo("USER_ALREADY_ACTIVE");
    }

    @Test
    @DisplayName("SELF_DISABLE_BLOCKED maps to 403")
    void selfDisableBlocked_mapsTo403() {
        BusinessRuleException ex = new BusinessRuleException("SELF_DISABLE_BLOCKED");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(resp.getBody().error().code()).isEqualTo("SELF_DISABLE_BLOCKED");
    }

    @Test
    @DisplayName("A code that does not appear in the catalog falls back to 500 (defensive — should never happen)")
    void unknownCode_fallsBackTo500() {
        // The handler must NOT crash on an unknown code (which would
        // be a service-layer bug — the contract is "every code is in
        // the catalog"). The fallback path returns 500 with the raw
        // code as the message so the failure is loud in the audit log.
        BusinessRuleException ex = new BusinessRuleException("DEFINITELY_NOT_A_REAL_CODE");

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(resp.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
        assertThat(resp.getBody().error().message()).contains("DEFINITELY_NOT_A_REAL_CODE");
    }

    @Test
    @DisplayName("Empty details map is omitted from the JSON envelope")
    void emptyDetails_omittedFromJson() {
        // ErrorEnvelope uses @JsonInclude(NON_EMPTY) on the details
        // map, so an empty map is omitted entirely. We assert the
        // map object is present-but-empty at the Java level (so the
        // caller can still read .details() without NPE) and trust
        // the Jackson annotation to drop it on the wire.
        BusinessRuleException ex = new BusinessRuleException("USER_NOT_FOUND", "x", Map.of());
        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);
        assertThat(resp.getBody().error().details()).isEmpty();
    }

    @Test
    @DisplayName("The handler does NOT swallow the exception's message — it surfaces on the envelope")
    void exceptionMessage_surfacesOnEnvelope() {
        BusinessRuleException ex = new BusinessRuleException("USER_NOT_FOUND", "Custom not-found message", null);

        ResponseEntity<ErrorEnvelope> resp = handler.handleBusinessRule(ex);

        assertThat(resp.getBody().error().message()).isEqualTo("Custom not-found message");
    }
}
