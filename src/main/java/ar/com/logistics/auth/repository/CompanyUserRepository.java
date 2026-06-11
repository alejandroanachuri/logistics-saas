package ar.com.logistics.auth.repository;

import ar.com.logistics.auth.domain.CompanyUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-user repository. The same interface is bound to BOTH
 * the {@code companyDataSource} EMF (for post-login queries
 * filtered by RLS) and the {@code systemDataSource} EMF (for the
 * registration write path and the per-tenant uniqueness
 * pre-checks). Each {@code @EnableJpaRepositories} declares this
 * package in its {@code basePackages}, so the JPA infrastructure
 * creates a separate repository proxy for each EMF.
 */
@Repository
public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    Optional<CompanyUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<CompanyUser> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
