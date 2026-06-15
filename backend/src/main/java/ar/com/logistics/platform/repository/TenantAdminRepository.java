package ar.com.logistics.platform.repository;

import ar.com.logistics.tenant.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) tenant repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}. The
 * registration service uses this exclusively on the write
 * path; the per-tenant read side goes through
 * {@link ar.com.logistics.tenant.repository.TenantRepository}
 * (RLS-filtered).
 *
 * <p>This interface lives in {@code ar.com.logistics.platform.repository}
 * (NOT {@code ar.com.logistics.tenant.repository.admin}) so the
 * per-DataSource {@code @EnableJpaRepositories(basePackages=...)}
 * lists are disjoint. If we put it under
 * {@code tenant.repository.admin}, the company-side
 * {@code @EnableJpaRepositories(basePackages="ar.com.logistics.tenant.repository")}
 * would also pick it up and the Spring context would fail to start
 * with {@code BeanDefinitionOverrideException}.
 */
@Repository
public interface TenantAdminRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByCuit(String cuit);

    boolean existsBySlug(String slug);

    boolean existsByCuit(String cuit);
}
