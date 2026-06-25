package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Customer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code customers} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16.
 *
 * <p>The {@link #findByTenantIdAndDni(UUID, String)} derived query is
 * what the PR-3a customer-creation service uses to enforce the
 * app-layer uniqueness check before INSERT (the DB does NOT have a
 * UNIQUE constraint on {@code (tenant_id, dni)} because the
 * {@code idx_customers_dni} is a non-unique index — DNI may be
 * repeated for FISICA customers with shared phones in some
 * jurisdictions, so we enforce uniqueness in code instead).
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByTenantIdAndDni(UUID tenantId, String dni);
}
