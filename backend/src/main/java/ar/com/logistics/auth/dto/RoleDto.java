package ar.com.logistics.auth.dto;

import java.util.UUID;

/**
 * Read-only projection of a {@code roles} row used by both the list /
 * detail responses and the {@code GET /api/v1/roles} endpoint.
 *
 * <p>The {@code description} is optional (some seeded roles may have
 * a {@code NULL} description); the field is included on the wire
 * regardless so clients can render it when present.
 */
public record RoleDto(UUID id, String name, String description) {}
