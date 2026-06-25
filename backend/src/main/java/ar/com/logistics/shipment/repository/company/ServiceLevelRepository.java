package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.ServiceLevel;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code service_levels} table
 * (V15). Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS-scoped via
 * V16.
 *
 * <p>PR-3a adds {@code findByTenantIdAndCode} and the lazy-seed
 * helper that creates the {@code STANDARD} service level on first
 * shipment creation.
 */
@Repository
public interface ServiceLevelRepository extends JpaRepository<ServiceLevel, UUID> {}
