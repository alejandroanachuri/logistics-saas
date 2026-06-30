package ar.com.logistics.shipment.controller;

import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.service.BranchService;
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
 * Read-only catalog endpoint for {@link Branch} under
 * {@code /api/v1/branches} (spec §B, etapa-3-envios PR-4 Chunk B
 * Part 1). Returns the tenant's active branches ordered by code so
 * the frontend can populate the shipment-form branch dropdown
 * without hard-coding.
 *
 * <p>Auth: any authenticated company user can read the catalog.
 * Mutation is Etapa-4 scope (admin-managed branches).
 */
@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {

    private final BranchService branchService;

    public BranchController(BranchService branchService) {
        this.branchService = branchService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN','COMPANY_OPERATOR','COMPANY_VIEWER')")
    public ResponseEntity<List<BranchDto>> list(@AuthenticationPrincipal JwtAuthentication auth) {
        UUID tenantId = currentTenantId();
        List<Branch> rows = branchService.list(tenantId);
        return ResponseEntity.ok(rows.stream().map(this::toDto).toList());
    }

    private static UUID currentTenantId() {
        return TenantContext.currentTenantId();
    }

    private BranchDto toDto(Branch b) {
        return new BranchDto(b.getId(), b.getCode(), b.getName(), b.getAddressId(), b.isActive());
    }

    /** Wire DTO for {@code GET /branches}. */
    public record BranchDto(UUID id, String code, String name, UUID addressId, boolean isActive) {}
}
