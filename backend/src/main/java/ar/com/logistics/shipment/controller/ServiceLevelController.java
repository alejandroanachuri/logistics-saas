package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.ServiceLevel;
import ar.com.logistics.shipment.service.ServiceLevelService;
import ar.com.logistics.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only catalog endpoint for {@link ServiceLevel} under
 * {@code /api/v1/service-levels} (spec §B, etapa-3-envios PR-4
 * Chunk B Part 1). Returns the tenant's active service levels
 * ordered by code so the frontend can populate the shipment-form
 * service-level dropdown without hard-coding.
 *
 * <p>Auth: any authenticated company user can read the catalog.
 * Mutation is Etapa-4 scope (admin-managed service levels).
 */
@RestController
@RequestMapping("/api/v1/service-levels")
public class ServiceLevelController {

    private final ServiceLevelService serviceLevelService;

    public ServiceLevelController(ServiceLevelService serviceLevelService) {
        this.serviceLevelService = serviceLevelService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<List<ServiceLevelDto>> list(@AuthenticationPrincipal JwtAuthentication auth) {
        UUID tenantId = currentTenantId();
        List<ServiceLevel> rows = serviceLevelService.list(tenantId);
        return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
    }

    private static UUID currentTenantId() {
        return TenantContext.currentTenantId();
    }

    private ServiceLevelDto toDto(ServiceLevel s) {
        return new ServiceLevelDto(s.getId(), s.getCode(), s.getName(), s.isActive());
    }

    /** Wire DTO for {@code GET /service-levels}. */
    public record ServiceLevelDto(UUID id, String code, String name, boolean isActive) {}
}
