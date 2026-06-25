package ar.com.logistics.shipment.repository.company;

import ar.com.logistics.shipment.domain.Package;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code packages} table (V15).
 * Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V16.
 *
 * <p>The FSM transitions land in PR-3b via a native
 * {@code @Modifying} query (mirrors the
 * {@code CompanyUserRoleRepository.insertRow} pattern) so the
 * {@code UPDATE packages SET status = :next ... WHERE id = :id AND
 * status = :expected} runs as a single round-trip with optimistic
 * concurrency baked into the WHERE clause.
 */
@Repository
public interface PackageRepository extends JpaRepository<Package, UUID> {}
