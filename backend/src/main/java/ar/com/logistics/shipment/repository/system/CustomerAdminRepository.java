package ar.com.logistics.shipment.repository.system;

import ar.com.logistics.shipment.domain.Customer;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) customer repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}.
 *
 * <p>Exclusively used by the public tracking portal (PR-4 Chunk C)
 * to look up the receiver customer by id, cross-tenant. The
 * company-side {@code CustomerRepository} is RLS-scoped via V16 and
 * would deny the lookup because no {@code app.current_tenant} is
 * set on the {@code /api/v1/public/**} path. The {@code findById}
 * derived query from {@link JpaRepository} is sufficient — the
 * service does not need any tenant predicate here.
 */
@Repository
public interface CustomerAdminRepository extends JpaRepository<Customer, UUID> {
    // No extra derived queries needed — findById is enough for the
    // receiver-name lookup in PublicTrackService.
}
