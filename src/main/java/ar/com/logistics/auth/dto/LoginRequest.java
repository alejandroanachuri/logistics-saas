package ar.com.logistics.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Wire shape for {@code POST /api/v1/auth/login}. The three fields map
 * 1:1 to the (tenantSlug, username, password) tuple validated
 * server-side; everything else (status, lockout, role) is read from
 * the database and never trusted from the client.
 *
 * <p>No {@code @Email} here: the username on the company path is the
 * {@code company_users.username} column, not the email address. The
 * login flow also accepts email as an alternative, but the v1
 * contract pins to username (see {@code company-user-auth} spec,
 * requirement "Company User Login").
 */
public record LoginRequest(
        @NotBlank @Size(min = 2, max = 12) String slug,
        @NotBlank @Size(min = 3, max = 30) String username,
        @NotBlank @Size(min = 8, max = 128) String password) {}
