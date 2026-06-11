package ar.com.logistics.reference.dto;

import ar.com.logistics.tenant.reference.Province;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Single entry in the {@code GET /api/v1/reference/provinces}
 * response. The {@code code} is the canonical enum name and is
 * stable across releases; the {@code displayName} is the
 * human-readable Spanish label the UI renders.
 */
public record ProvinceResponse(@NotBlank String code, @NotBlank String displayName) {

    /** Convenience mapper. */
    public static ProvinceResponse from(Province p) {
        return new ProvinceResponse(p.code(), p.displayName());
    }

    /**
     * Top-level wire shape for the provinces catalog endpoint. The
     * spec (lines 28-33) prescribes a JSON array of 24 entries; this
     * wrapper is a forward-compatible variant (some clients prefer
     * an envelope; v1 sends the array directly via
     * {@link #data()}).
     */
    public record ProvincesResponse(@NotNull List<ProvinceResponse> data) {}
}
