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
}
