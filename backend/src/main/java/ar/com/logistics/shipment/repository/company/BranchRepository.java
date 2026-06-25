package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Branch;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code branches} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. RLS-scoped via
 * V16.
 *
 * <p>PR-3a adds {@code findByTenantIdAndCode} and the lazy-seed
 * helper that creates the {@code PRINCIPAL} branch on first
 * shipment creation.
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, UUID> {}
