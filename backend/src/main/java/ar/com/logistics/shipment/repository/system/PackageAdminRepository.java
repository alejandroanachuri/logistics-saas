package ar.com.logistics.shipment.repository.system;

import ar.com.logistics.shipment.domain.Package;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) package repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}.
 *
 * <p>The company-side {@code PackageRepository} is RLS-scoped via V16
 * and would DENY the public tracking lookup because no
 * {@code app.current_tenant} is set on the {@code /api/v1/public/**}
 * path. This mirror interface exists so the public tracking portal
 * (PR-4 Chunk C) can read packages for a shipment without any tenant
 * binding.
 */
@Repository
public interface PackageAdminRepository extends JpaRepository<Package, UUID> {

    /**
     * Every package belonging to a shipment, cross-tenant. Runs under
     * {@code app_admin} (BYPASSRLS) so no tenant predicate is needed.
     */
    List<Package> findByShipmentId(UUID shipmentId);
}
