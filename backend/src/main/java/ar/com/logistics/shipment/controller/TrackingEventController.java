package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.TrackingEvent;
import ar.com.logistics.shipment.service.TrackingEventService;
import ar.com.logistics.shipment.service.TrackingEventService.RecordEventRequest;
import ar.com.logistics.tenant.TenantContext;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for package-level tracking events under
 * {@code /api/v1/packages/{packageId}/events/**}
 * (spec §C, etapa-3-envios PR-4 Chunk B Part 2).
 *
 * <p>Two endpoints:
 * <ul>
 *   <li>{@code POST /} — record a new tracking event. Drives the
 *       package FSM (or fans the transition out to every package in
 *       the shipment when the event is shipment-level).
 *       Admin+Operator.</li>
 *   <li>{@code GET  /} — full event history for the package,
 *       oldest-first. Admin+Operator+Viewer.</li>
 * </ul>
 *
 * <p>Tenant resolution: tenant id from {@link TenantContext}. The
 * actor id ({@code currentUserId()}) is passed to the service for
 * audit + event.userId.
 *
 * <p>Note: the URL path uses {@code /packages/{id}/events} where the
 * {@code id} may be either a real package id or a shipment id —
 * {@link TrackingEventService#record} resolves the target via its
 * own routing logic.
 */
@RestController
@RequestMapping("/api/v1/packages/{packageId}/events")
public class TrackingEventController {

    private final TrackingEventService trackingEventService;

    public TrackingEventController(TrackingEventService trackingEventService) {
        this.trackingEventService = trackingEventService;
    }

    // -------------------------------------------------------------------
    //  POST /              record a new tracking event
    // -------------------------------------------------------------------

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR')")
    public ResponseEntity<TrackingEventDto> record(
            @AuthenticationPrincipal JwtAuthentication auth,
            @PathVariable("packageId") UUID packageId,
            @Valid @RequestBody RecordEventRequestDto req) {
        UUID tenantId = currentTenantId();
        UUID userId = currentUserId(auth);

        RecordEventRequest serviceReq = new RecordEventRequest(
                req.eventType(), req.eventTimestamp(), req.payload(), req.sourceIp(), req.userAgent());

        TrackingEvent saved = trackingEventService.record(tenantId, userId, packageId, serviceReq);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(saved));
    }

    // -------------------------------------------------------------------
    //  GET /               list events for the package (oldest-first)
    // -------------------------------------------------------------------

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<List<TrackingEventDto>> list(
            @AuthenticationPrincipal JwtAuthentication auth, @PathVariable("packageId") UUID packageId) {
        UUID tenantId = currentTenantId();
        List<TrackingEvent> events = trackingEventService.list(tenantId, packageId);
        List<TrackingEventDto> dtos = new ArrayList<>(events.size());
        for (TrackingEvent e : events) {
            dtos.add(toDto(e));
        }
        return ResponseEntity.ok(dtos);
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    private static UUID currentTenantId() {
        return TenantContext.currentTenantId();
    }

    private static UUID currentUserId(JwtAuthentication auth) {
        if (auth != null && auth.currentUserId() != null) {
            return auth.currentUserId();
        }
        org.springframework.security.core.Authentication a =
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();
        if (a != null && a.getName() != null) {
            try {
                return UUID.fromString(a.getName());
            } catch (IllegalArgumentException ex) {
                // not a UUID subject — leave null and let the service
                // surface the issue.
            }
        }
        return null;
    }

    private TrackingEventDto toDto(TrackingEvent e) {
        return new TrackingEventDto(
                e.getId(),
                e.getPackageId(),
                e.getEventType(),
                e.getEventTimestamp(),
                e.getBranchId(),
                e.getUserId(),
                e.getEventSource(),
                e.getMetadata(),
                e.getCreatedAt());
    }

    // -------------------------------------------------------------------
    //  DTOs (nested records)
    // -------------------------------------------------------------------

    /** One tracking-event wire shape. */
    public record TrackingEventDto(
            UUID id,
            UUID packageId,
            String eventType,
            Instant eventTimestamp,
            UUID branchId,
            UUID userId,
            String eventSource,
            String metadata,
            Instant createdAt) {}

    /** Wire DTO for {@code POST /packages/{id}/events}. */
    public record RecordEventRequestDto(
            String eventType, Instant eventTimestamp, Map<String, Object> payload, String sourceIp, String userAgent) {}
}
