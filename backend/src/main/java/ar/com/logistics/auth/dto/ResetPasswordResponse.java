package ar.com.logistics.auth.dto;

import java.util.UUID;

/**
 * Response body for {@code POST /api/v1/company-users/{id}/reset-password}.
 * Returns only the {userId, username} pair (no full user detail) plus
 * the one-shot cleartext {@code temporaryPassword} and the warning
 * string. The frontend uses the {@code temporaryPassword} field to
 * render the password-reveal modal.
 */
public record ResetPasswordResponse(UUID userId, String username, String temporaryPassword, String passwordWarning) {

    public static final String DEFAULT_PASSWORD_WARNING = CreateCompanyUserResponse.DEFAULT_PASSWORD_WARNING;
}
