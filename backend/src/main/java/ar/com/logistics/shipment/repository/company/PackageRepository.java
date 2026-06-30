package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Package;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code packages} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16.
 *
 * <p>The {@link #findByIdAndTenantIdForUpdate(UUID, UUID)} method
 * (PR-3b) acquires a {@link LockModeType#PESSIMISTIC_WRITE} on the
 * row — Postgres {@code SELECT ... FOR UPDATE} — so concurrent
 * tracking-event registrations for the same package serialize at the
 * DB level. The FSM transition check
 * ({@code PackageFsm.isValidTransition}) runs INSIDE this lock
 * window, so two parallel {@code record()} calls cannot both observe
 * the same {@code currentStatus} and double-apply a transition.
 *
 * <p>The {@link #findByShipmentId(UUID)} derived query backs the
 * cascade path for shipment-level tracking events
 * ({@code shipment_validated}, {@code shipment_rejected}): the
 * service resolves the shipment, then loads all of its packages to
 * apply the transition uniformly.
 */
@Repository
public interface PackageRepository extends JpaRepository<Package, UUID> {

    /**
     * Tenant-scoped lookup that takes a pessimistic write lock on the
     * package row. The lock is released at transaction commit / rollback.
     * Used by {@code TrackingEventService.record} to serialize concurrent
     * FSM transitions for the same package. The {@code tenantId} predicate
     * is a defense-in-depth check: RLS already scopes reads to the
     * caller's tenant, but the JPQL filter documents the intent.
     *
     * @param id the package id
     * @param tenantId the caller's tenant (RLS scope)
     * @return the locked package, or empty when the row is missing or belongs
     *         to a different tenant
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Package p WHERE p.id = :id AND p.tenantId = :tenantId")
    Optional<Package> findByIdAndTenantIdForUpdate(@Param("id") UUID id, @Param("tenantId") UUID tenantId);

    /**
     * Every package belonging to a shipment. Used by shipment-level tracking
     * events to fan out the transition to each package in the shipment.
     * RLS-scoped via V16 — returns only the caller's tenant's packages.
     */
    List<Package> findByShipmentId(UUID shipmentId);
}
