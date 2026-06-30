package ar.com.logistics.shipment.repository.system;

import ar.com.logistics.shipment.domain.TrackingEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) tracking-event repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}.
 *
 * <p>Exclusively used by the public tracking portal (PR-4 Chunk C)
 * to load the visible subset of a package's timeline, cross-tenant.
 * The company-side {@code TrackingEventRepository} is RLS-scoped via
 * V16 and would deny the lookup because no
 * {@code app.current_tenant} is set on the {@code /api/v1/public/**}
 * path.
 */
@Repository
public interface TrackingEventAdminRepository extends JpaRepository<TrackingEvent, UUID> {

    /**
     * Full event history for a package, oldest-first, cross-tenant.
     * The {@code PublicTrackService} is the boundary that filters by
     * the visibility whitelist (PRD §9.5).
     */
    List<TrackingEvent> findByPackageIdOrderByEventTimestampAsc(UUID packageId);
}
