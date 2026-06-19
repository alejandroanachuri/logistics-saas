package ar.com.logistics.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code PATCH /api/v1/company-users/{id}}. All
 * fields are optional (a missing field is "no change to that
 * column"), but the request as a whole MUST change at least one
 * field — that constraint is enforced via the {@link #isAtLeastOneFieldPresent()}
 * method (Bean-Validation {@code @AssertTrue} on a boolean getter
 * is the standard pattern for cross-field constraints on records).
 *
 * <p>Bean-validation constraints:
 * <ul>
 *   <li>{@code firstName} / {@code lastName} — optional,
 *       {@code size <= 100}.</li>
 *   <li>{@code email} — optional, valid email format when present.</li>
 *   <li>{@code roleIds} — optional list of UUIDs. The at-least-one-role
 *       invariant (spec §B.4) is enforced in the service layer; the
 *       controller's Bean-Validation only accepts the null OR non-empty
 *       shape — an empty list here triggers the
 *       {@code isAtLeastOneFieldPresent()} rule below because an empty
 *       list is treated as "no change" by the service anyway.</li>
 * </ul>
 */
public record UpdateCompanyUserRequest(
        @Size(max = 100) String firstName, @Size(max = 100) String lastName, @Email String email, List<UUID> roleIds) {

    /**
     * Cross-field rule: at least one field must be supplied (and
     * non-empty). Empty {@code roleIds} is treated as "no change"
     * so it does NOT count as a field being present — the service
     * layer's at-least-one-role invariant would reject an empty set
     * anyway.
     */
    @AssertTrue(message = "at least one field must be present")
    public boolean isAtLeastOneFieldPresent() {
        if (firstName != null && !firstName.isBlank()) {
            return true;
        }
        if (lastName != null && !lastName.isBlank()) {
            return true;
        }
        if (email != null && !email.isBlank()) {
            return true;
        }
        if (roleIds != null && !roleIds.isEmpty()) {
            return true;
        }
        return false;
    }
}
