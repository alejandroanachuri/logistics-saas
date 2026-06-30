package ar.com.logistics.shipment.controller;

import ar.com.logistics.shipment.service.PublicTrackService;
import ar.com.logistics.shipment.service.PublicTrackService.PublicTrackResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public tracking portal endpoint (etapa-3-envios, PR-4 Chunk C).
 *
 * <p>This controller is the only authenticated-free path that
 * crosses tenants. It is mounted under {@code /api/v1/public/**},
 * which is permitted in {@code SecurityConfig} without
 * authentication. The {@code AuthenticationFilter} short-circuits
 * on the {@code /api/v1/public/} prefix; the {@code RateLimitFilter}
 * throttles requests at {@code publicTrackPerHour} per client IP
 * (Bucket4j, configured in PR-4 Chunk A).
 *
 * <p>The controller does NOT use {@code @PreAuthorize} or
 * {@code @AuthenticationPrincipal} — by design. There is no tenant
 * context to resolve on this path; the {@link PublicTrackService}
 * runs under {@code systemDataSource} (BYPASSRLS) and looks up the
 * shipment by its globally-unique {@code tracking_id}.
 *
 * <p>Route: {@code GET /api/v1/public/track/{lgstid}}.
 */
@RestController
@RequestMapping("/api/v1/public/track")
public class PublicTrackController {

    private final PublicTrackService publicTrackService;

    public PublicTrackController(PublicTrackService publicTrackService) {
        this.publicTrackService = publicTrackService;
    }

    /**
     * Look up a shipment by its public tracking id and return a
     * public-safe response. {@code 200 OK} on success,
     * {@code 404 TRACKING_NOT_FOUND} when the id does not match any
     * shipment (handled by {@code GlobalExceptionHandler}).
     */
    @GetMapping("/{lgstid}")
    public PublicTrackResponse track(@PathVariable("lgstid") String lgstid) {
        return publicTrackService.get(lgstid);
    }
}
