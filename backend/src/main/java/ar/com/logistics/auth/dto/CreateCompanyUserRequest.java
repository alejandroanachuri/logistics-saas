package ar.com.logistics.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Request body for {@code POST /api/v1/company-users}. Carries the
 * fields the admin must supply to create a tenant-scoped user plus
 * the 1+ roles to assign at creation time.
 *
 * <p>Bean-validation constraints:
 * <ul>
 *   <li>{@code username} — required, 3-30 chars (matches
 *       {@code UsernameValidator} used by the registration flow).</li>
 *   <li>{@code email} — required, valid email format.</li>
 *   <li>{@code firstName} / {@code lastName} — optional (the spec
 *       allows blank; the registration path requires them but the
 *       team-management path doesn't — admins sometimes onboard
 *       placeholder identities first).</li>
 *   <li>{@code password} — optional. When {@code null} or blank the
 *       service generates a strong 12-char password via
 *       {@code PasswordGeneratorService}. When present it must
 *       satisfy {@code PasswordValidator} (the check happens in the
 *       service layer — the controller's Bean-Validation only
 *       covers basic length).</li>
 *   <li>{@code roleIds} — required, non-empty list of UUIDs
 *       (the at-least-one rule from spec §B.2 + the COMPANY-scope
 *       rule from C7 are enforced in the service layer; the
 *       controller's Bean-Validation only covers non-empty).</li>
 * </ul>
 */
public record CreateCompanyUserRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank @Email String email,
        @Size(max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Size(max = 128) String password,
        @NotEmpty List<UUID> roleIds) {}
