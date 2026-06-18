package ar.com.logistics.auth.repository.company;

import ar.com.logistics.auth.domain.CompanyUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Company-side company-user repository. Bound to the
 * {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. The split
 * from {@code auth.repository.system.CompanyUserAdminRepository}
 * exists so the per-DataSource
 * {@code @EnableJpaRepositories(basePackages=...)} lists are
 * disjoint (otherwise the same interface class is registered
 * twice in two different persistence units and the Spring
 * context fails to start with
 * {@code BeanDefinitionOverrideException}).
 */
@Repository
public interface CompanyUserRepository extends JpaRepository<CompanyUser, UUID> {

    Optional<CompanyUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<CompanyUser> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);

    Optional<CompanyUser> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndEmailAndIdNot(UUID tenantId, String email, UUID excludedId);

    /**
     * Count how many active rows for {@code userId} satisfy the
     * {@code isFirstAdmin} derivation:
     * {@code created_by IS NULL AND deleted_at IS NULL}. Returns 1
     * iff the user is the registration-time first admin AND has not
     * been soft-deleted; 0 otherwise.
     *
     * <p>Implemented as a derived query so Hibernate emits a single
     * COUNT query that runs under RLS on the company pool
     * (cross-tenant isolation baked in — a user id from another
     * tenant is invisible).
     */
    long countByIdAndCreatedByIsNullAndDeletedAtIsNull(UUID id);
}
