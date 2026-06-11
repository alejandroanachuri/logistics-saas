package ar.com.logistics.tenant.repository.admin;

import ar.com.logistics.tenant.domain.Tenant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * System-side (admin) tenant repository. Bound to the
 * {@code systemDataSource} EMF (BYPASSRLS) by
 * {@link ar.com.logistics.config.SystemJpaConfig}. The
 * registration service uses this exclusively on the write path
 * — the per-tenant read side goes through
 * {@link ar.com.logistics.tenant.repository.TenantRepository}
 * (RLS-filtered).
 *
 * <p>The repository lives in its own sub-package
 * ({@code .admin}) to keep the per-DataSource
 * {@code @EnableJpaRepositories} basePackages list unambiguous:
 * the system config declares
 * {@code {"ar.com.logistics.tenant.repository.admin",
 * "ar.com.logistics.auth.repository"}}, and the company config
 * declares {@code {"ar.com.logistics.tenant.repository",
 * "ar.com.logistics.auth.repository"}}. The two packages share
 * the entity class {@link Tenant} (Hibernate supports the same
 * entity in multiple persistence units) but route through
 * different EMFs and connection pools.
 */
@Repository
public interface TenantAdminRepository extends JpaRepository<Tenant, UUID> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByCuit(String cuit);

    boolean existsBySlug(String slug);

    boolean existsByCuit(String cuit);
}
