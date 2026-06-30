package ar.com.logistics.shipment.service;

import ar.com.logistics.shipment.domain.Branch;
import ar.com.logistics.shipment.repository.company.BranchRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only catalog service for {@link Branch} — exposes
 * {@code list(tenantId)} for the {@code GET /branches} endpoint
 * (etapa-3-envios, PR-4).
 *
 * <p>Branches are inert reference data. CRUD on this table is
 * Etapa 4 scope — this service deliberately exposes no mutation
 * methods. The {@code PRINCIPAL} branch is lazy-seeded the first
 * time a tenant creates a shipment; that seed lives in
 * {@code ShipmentService.create} (PR-3a Chunk B), not here.
 *
 * <p>Reads are RLS-scoped via V16. Soft-delete is signalled by
 * {@code deletedAt != null}; active filtering is delegated to the
 * repository derived query
 * {@link BranchRepository#findByTenantIdAndIsActiveTrueOrderByCode(UUID)},
 * which orders the result by {@code code} for a stable catalog
 * dropdown ordering.
 */
@Service
public class BranchService {

    private final BranchRepository branchRepository;

    public BranchService(BranchRepository branchRepository) {
        this.branchRepository = branchRepository;
    }

    /**
     * Active branches for the tenant, ordered by code. Returns an
     * empty list when the tenant has no branches — for a
     * freshly-onboarded tenant this is the expected state until the
     * first shipment triggers the lazy-seed in
     * {@code ShipmentService.create}.
     */
    @Transactional("companyTransactionManager")
    public List<Branch> list(UUID tenantId) {
        return branchRepository.findByTenantIdAndIsActiveTrueOrderByCode(tenantId);
    }
}
