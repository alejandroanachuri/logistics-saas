package ar.com.logistics.tenant.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Response body for {@code GET /api/v1/tenants/me/cuit-availability?cuit=...}.
 *
 * <p>Wire shape (per spec lines 611-622):
 * <pre>{ "cuit": "30-71234567-8", "available": true, "valid": true }</pre>
 *
 * <p>Two separate booleans because the wizard distinguishes
 * "format is wrong" (client must fix the input) from "format is
 * right but already taken" (different UX). When {@code valid}
 * is false, {@code available} is also false and {@code reason}
 * carries the same {@code VALIDATION_ERROR} code used by the
 * other validation responses.
 */
public record CuitAvailabilityResponse(
        @NotBlank String cuit,
        boolean available,
        boolean valid,
        @Size(max = 50) @JsonInclude(JsonInclude.Include.NON_NULL) String reason) {

    public static CuitAvailabilityResponse validAndAvailable(String cuit) {
        return new CuitAvailabilityResponse(cuit, true, true, null);
    }

    public static CuitAvailabilityResponse validButTaken(String cuit) {
        return new CuitAvailabilityResponse(cuit, false, true, "CUIT_ALREADY_REGISTERED");
    }

    public static CuitAvailabilityResponse invalidFormat(String cuit) {
        return new CuitAvailabilityResponse(cuit, false, false, "VALIDATION_ERROR");
    }
}
