package ar.com.logistics.auth.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Detail projection returned by {@code GET /api/v1/company-users/{id}},
 * {@code PATCH /api/v1/company-users/{id}}, and
 * {@code POST /api/v1/company-users/{id}/reactivate}. Carries every
 * column the spec §B.3 surfaces, EXCEPT {@code passwordHash}
 * (spec C2 — never exposed) and {@code tenantId} (resolved from the
 * JWT — never echoed on a row).
 */
public record CompanyUserDetailDto(
        UUID id,
        String username,
        String email,
        String firstName,
        String lastName,
        String status,
        boolean emailVerified,
        List<RoleDto> roles,
        int failedLoginAttempts,
        Instant lockedUntil,
        Instant lastLoginAt,
        Instant createdAt,
        Instant updatedAt,
        boolean isFirstAdmin) {}
