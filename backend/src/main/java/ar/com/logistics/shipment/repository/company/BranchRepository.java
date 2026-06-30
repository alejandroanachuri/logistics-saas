package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Branch;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code branches} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS-scoped via
 * V16.
 *
 * <p>PR-3b (Chunk A) adds
 * {@link #findByTenantIdAndIsActiveTrueOrderByCode(UUID)} used by
 * {@code BranchService.list} for the {@code GET /branches}
 * catalog endpoint. The lazy-seed {@code PRINCIPAL} branch lookup
 * lives in {@code ShipmentService.create}.
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {

    /**
     * Active branches for the given tenant, ordered by code (stable
     * catalog ordering for the dropdown UI). Soft-deleted rows
     * ({@code deletedAt != null}) are excluded by definition because
     * the method targets the {@code isActive} flag, which is the
     * canonical "active catalog row" marker on this table.
     */
    List<Branch> findByTenantIdAndIsActiveTrueOrderByCode(UUID tenantId);
}
