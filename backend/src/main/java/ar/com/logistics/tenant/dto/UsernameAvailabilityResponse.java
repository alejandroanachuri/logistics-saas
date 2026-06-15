package ar.com.logistics.tenant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Response body for
 * {@code GET /api/v1/tenants/me/username-availability?slug=&username=}.
 *
 * <p>Wire shape (per spec lines 248-309):
 * <pre>{ "slug": "mvr", "username": "facu", "available": true }</pre>
 *
 * <p>Reason codes (when present): {@code SLUG_NOT_FOUND},
 * {@code USERNAME_ALREADY_TAKEN}, {@code USERNAME_RESERVED}.
 */
public record UsernameAvailabilityResponse(
        @NotBlank String slug,
        @NotBlank String username,
        boolean available,
        @Size(max = 50) @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    public static UsernameAvailabilityResponse available(String slug, String username) {
        return new UsernameAvailabilityResponse(slug, username, true, null);
    }

    public static UsernameAvailabilityResponse unavailable(String slug, String username, String reason) {
        return new UsernameAvailabilityResponse(slug, username, false, reason);
    }
}
