package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code shipments} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16.
 *
 * <p>The {@link #findByTrackingId(String)} derived query backs the
 * LGST collision-retry path in
 * {@code LgstGeneratorService.generateUnique()}: when
 * {@code gen()} produces a code that already exists, the service
 * catches the {@code DataIntegrityViolationException} and re-tries
 * after a short backoff. This lookup is the fallback path for the
 * public tracking endpoint (PR-4) which needs to resolve
 * {@code tracking_id} → shipment under BYPASSRLS — that one uses
 * {@code ShipmentAdminRepository} on the system pool.
 */
@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {

    Optional<Shipment> findByTrackingId(String trackingId);
}
