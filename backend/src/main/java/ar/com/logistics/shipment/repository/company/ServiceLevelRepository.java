package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.ServiceLevel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code service_levels} table
 * (V15). Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS-scoped via
 * V16.
 *
 * <p>PR-3b (Chunk A) adds
 * {@link #findByTenantIdAndIsActiveTrueOrderByCode(UUID)} used by
 * {@code ServiceLevelService.list} for the
 * {@code GET /service-levels} catalog endpoint. The lazy-seed
 * {@code STANDARD} service level lookup lives in
 * {@code ShipmentService.create}.
 */
@Repository
public interface ServiceLevelRepository extends JpaRepository<ServiceLevel, UUID> {

    /**
     * Active service levels for the given tenant, ordered by code
     * (stable catalog ordering for the dropdown UI). Soft-deleted
     * rows ({@code deletedAt != null}) are excluded by definition
     * because the method targets the {@code isActive} flag, which
     * is the canonical "active catalog row" marker on this table.
     */
    List<ServiceLevel> findByTenantIdAndIsActiveTrueOrderByCode(UUID tenantId);
}
