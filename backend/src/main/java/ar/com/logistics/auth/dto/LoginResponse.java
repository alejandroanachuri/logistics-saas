package ar.com.logistics.auth.dto;

import java.util.List;
import java.util.UUID;

/**
 * Wire shape for the {@code POST /api/v1/auth/login} success body
 * and the {@code GET /api/v1/auth/me} response. Per spec, the field
 * set is identical for both endpoints (login returns the same
 * projection the client will cache after {@code /me} confirms it).
 *
 * <p>Note: the JWT {@code access_token} and refresh UUID are NOT in
 * the body — they live in HttpOnly cookies. The body carries
 * {@code expiresIn} (seconds until access-token expiry) so the
 * frontend can schedule a refresh.
 *
 * <p>Etapa-2 (PR-3) breaking change: the singular {@code role: String}
 * field has been replaced with {@code roles: List<String>} to support
 * the multi-role junction introduced in PR-1. Existing single-role
 * users continue to receive a singleton list (one element).
 */
public record LoginResponse(User user, long expiresIn) {

    /**
     * Tenant-scoped user projection. The {@code scope} is always
     * {@code "COMPANY"} on this endpoint — platform users have
     * their own login flow at {@code /api/v1/platform/auth/login}.
     */
    public record User(
            UUID id,
            UUID tenantId,
            String tenantSlug,
            String username,
            String email,
            String firstName,
            String lastName,
            List<String> roles,
            String scope,
            boolean emailVerified) {}
}
