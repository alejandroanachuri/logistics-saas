package ar.com.logistics.tenant.repository;

import ar.com.logistics.tenant.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
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
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByCuit(String cuit);

    boolean existsBySlug(String slug);

    boolean existsByCuit(String cuit);
}
