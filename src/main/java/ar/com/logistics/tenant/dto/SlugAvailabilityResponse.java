package ar.com.logistics.tenant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Response body for {@code GET /api/v1/tenants/me/slug-availability?slug=...}.
 *
 * <p>Wire shape (per spec lines 590-609):
 * <pre>{ "slug": "mvr", "available": true }</pre>
 *
 * <p>When the slug is NOT available, {@code reason} is present
 * with one of the canonical codes from {@code design §6}:
 * {@code RESERVED_SLUG}, {@code SLUG_ALREADY_TAKEN},
 * {@code VALIDATION_ERROR}.
 */
public record SlugAvailabilityResponse(
        @NotBlank String slug,
        boolean available,
        @Size(max = 50) @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    /** Convenience factory for the "available" case. */
    public static SlugAvailabilityResponse available(String slug) {
        return new SlugAvailabilityResponse(slug, true, null);
    }

    /** Convenience factory for the "not available" case. */
    public static SlugAvailabilityResponse unavailable(String slug, String reason) {
        return new SlugAvailabilityResponse(slug, false, reason);
    }
}
