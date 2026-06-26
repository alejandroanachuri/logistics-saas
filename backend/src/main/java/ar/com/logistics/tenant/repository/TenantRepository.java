package ar.com.logistics.tenant.repository;

import ar.com.logistics.tenant.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Company-side tenant repository. Bound to the
 * {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads
 * through RLS — a query for a tenant id the caller is not
 * authorized for returns 0 rows.
 *
 * <p>Future reads of a single tenant from the post-login company
 * flow go through this interface (e.g. PR5's {@code GET /tenants/me}).
 *
 * <p>PR-3a adds {@link #incrementShipmentCount(UUID)}: an atomic
 * native UPDATE that bumps {@code current_month_shipment_count}
 * by 1 in a single round-trip (no SELECT FOR UPDATE). The
 * columns live on {@code tenants} since V17 but are not mapped
 * on the {@link Tenant} entity (PR-3a scope was strictly service
 * + tests, no entity edits), so the query references the
 * database columns directly.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByCuit(String cuit);

    boolean existsBySlug(String slug);

    boolean existsByCuit(String cuit);

    /**
     * Atomic increment of the per-tenant monthly shipment counter.
     *
     * <p>Single-statement {@code UPDATE ... SET col = col + 1
     * WHERE id = :id} — the row-level lock acquired by UPDATE
     * is sufficient for correctness, no SELECT FOR UPDATE needed.
     * The counter is reset monthly by a cron job in Etapa 10.
     *
     * @param tenantId the tenant to bump (RLS-scoped)
     * @return the number of rows updated (1 if the tenant exists, 0 otherwise)
     */
    @Modifying
    @Query(
            value = "UPDATE public.tenants SET current_month_shipment_count = current_month_shipment_count + 1 "
                    + "WHERE id = :tenantId",
            nativeQuery = true)
    int incrementShipmentCount(@Param("tenantId") UUID tenantId);
}
