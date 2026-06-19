package ar.com.logistics.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * List-row projection of a {@code CompanyUser} returned by
 * {@code GET /api/v1/company-users}. Mirrors the spec §B.1 contract:
 * id, username, email, firstName, lastName, status, roles[],
 * isFirstAdmin, lastLoginAt, createdAt. No {@code passwordHash}
 * (spec C2). No {@code tenantId} either — the list is always
 * scoped to a single tenant resolved from the JWT, so echoing it
 * on every row is redundant.
 */
public record CompanyUserSummaryDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String status,
        List<RoleDto> roles,
        boolean isFirstAdmin,
        Instant lastLoginAt,
        Instant createdAt) {}
