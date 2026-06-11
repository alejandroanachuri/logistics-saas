package ar.com.logistics.auth.repository.system;

import ar.com.logistics.auth.domain.CompanyUser;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) company-user repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}. The
 * registration service uses this exclusively on the write
 * path to check per-tenant uniqueness before INSERT.
 *
 * <p>This interface is a deliberate split from
 * {@code auth.repository.company.CompanyUserRepository} so
 * each DataSource has its own repository proxy. See the
 * {@code auth.repository.company} javadoc for the rationale.
 */
@Repository
public interface CompanyUserAdminRepository extends JpaRepository<CompanyUser, UUID> {

    Optional<CompanyUser> findByTenantIdAndUsername(UUID tenantId, String username);

    Optional<CompanyUser> findByTenantIdAndEmail(UUID tenantId, String email);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    boolean existsByTenantIdAndEmail(UUID tenantId, String email);
}
