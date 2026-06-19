package ar.com.logistics.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * Wire shape for {@code GET /api/v1/auth/me}. The response is
 * the projection of the {@code access_token} JWT claims — the
 * same shape {@code /auth/login} returns, served from a
 * different source.
 *
 * <p>Mirrors {@code LoginResponse.User} so the client can reuse
 * one TypeScript type for both endpoints. The two are kept
 * distinct (rather than aliased) so a future divergence in the
 * contract — e.g. "the /me response includes extra DB fields
 * the login response does not" — is a single-file change.
 *
 * <p>Note: no {@code firstName} / {@code lastName} / {@code
 * email} here. The spec pins {@code /me} to "echo the
 * access_token claims", so the projection is claim-shaped and
 * does NOT round-trip through the database. A future
 * {@code /api/v1/company-users/me} endpoint would query the DB
 * and return a richer shape.
 *
 * <p>Etapa-2 (PR-3) breaking change: the singular {@code role: String}
 * field has been replaced with {@code roles: List<String>}.
 */
public record MeResponse(User user) {

    public record User(
            UUID id,
            UUID tenantId,
            String tenantSlug,
            String username,
            List<String> roles,
            String scope,
            long expiresIn) {}
}
