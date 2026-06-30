package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Address;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code addresses} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16 (tenant filter via the {@code tenant_id}
 * column).
 *
 * <p>No custom query methods yet — PR-3a adds
 * {@code findByTenantIdAndCustomerId} when the
 * address-catalog endpoints are built.
 */
@Repository
public interface AddressRepository extends JpaRepository<Address, UUID> {}
