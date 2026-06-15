package ar.com.logistics.auth.dto;

import java.util.UUID;

/**
 * Response body for a successful {@code POST /api/v1/auth/register}.
 *
 * <p>The shape mirrors the PRD (lines 538-573):
 * <pre>
 * { "tenant": { "id", "slug", "legalName", "cuit" },
 *   "user":   { "id", "username", "email", "firstName", "lastName", "role" },
 *   "emailVerificationRequired": true }
 * </pre>
 *
 * <p>Flat fields are used here (not nested records) so Jackson can
 * serialize them at the top level without an extra wrapper class.
 * The 201 response does NOT include a {@code Set-Cookie} header —
 * auto-login is deferred to v1's login step (PRD line 588).
 */
public record RegisterResponse(
        UUID tenantId,
        String slug,
        String legalName,
        String cuit,
        UUID userId,
        String username,
        String email,
        String firstName,
        String lastName,
        String role,
        boolean emailVerificationRequired) {}
