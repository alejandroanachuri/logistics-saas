package ar.com.logistics.shipment.repository.system;

import ar.com.logistics.shipment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) shipment repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig} (the
 * {@code @EnableJpaRepositories} on
 * {@code ar.com.logistics.shipment.repository.system} routes this
 * interface to the system pool).
 *
 * <p>This interface is a deliberate split from
 * {@code ar.com.logistics.shipment.repository.company.ShipmentRepository}
 * so each DataSource has its own repository proxy. The company-side
 * repository is RLS-scoped via V16 — it would DENY the public
 * tracking lookup because no {@code app.current_tenant} is set on
 * the {@code /api/v1/public/**} path. The public tracking portal
 * (PR-4 Chunk C) uses this repository exclusively to look up
 * shipments across tenants by {@code tracking_id}.
 *
 * <p>Reads via this repository see ALL tenants' shipments; the
 * tracking id is globally unique by DB constraint, so there is no
 * risk of an ambiguous match. The service-layer boundary in
 * {@code PublicTrackService} projects the response to public-safe
 * fields only — internal IDs and tenant references never cross the
 * boundary.
 */
@Repository
public interface ShipmentAdminRepository extends JpaRepository<Shipment, UUID> {

    /**
     * Look up a shipment by its global-unique public tracking id.
     * Runs under {@code app_admin} (BYPASSRLS), so the caller's
     * tenant context is irrelevant — the result is whichever row
     * holds the matching {@code tracking_id} (or empty).
     */
    Optional<Shipment> findByTrackingId(String trackingId);
}
