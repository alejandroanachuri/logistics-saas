package ar.com.logistics.auth.repository.system;

import ar.com.logistics.auth.domain.CompanyUserRole;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * System-side (BYPASSRLS) counterpart to
 * {@code auth.repository.company.CompanyUserRoleRepository}. Exists
 * for the registration path, which inserts the first
 * {@code COMPANY_ADMIN} role assignment under {@code app_admin}
 * BEFORE any {@code app.current_tenant} context is in scope (the
 * tenant is being created in the same transaction).
 *
 * <p>Only the {@link #insertRow} method is exposed here because that
 * is the only write the registration path needs. The
 * many-to-many reads/writes from an authenticated company session
 * live on the company-side repo (RLS-scoped via V14).
 *
 * <p>The split mirrors the {@code CompanyUserAdminRepository} /
 * {@code CompanyUserRepository} precedent: per-DataSource repository
 * proxies so the per-EMF {@code @EnableJpaRepositories} base package
 * lists stay disjoint (otherwise Spring registers the same interface
 * class twice in two different persistence units and the context
 * fails to start).
 */
public interface CompanyUserRoleAdminRepository
        extends JpaRepository<CompanyUserRole, CompanyUserRole.CompanyUserRoleId> {

    /**
     * Insert one junction row, letting the database fill
     * {@code assigned_at} via its {@code DEFAULT NOW()}.
     * {@code ON CONFLICT DO NOTHING} keeps the registration path
     * idempotent under partial retries: a second call is a no-op
     * rather than a unique-constraint violation. Mirrors the
     * company-side repo's native insert verbatim — the SQL is the
     * same, only the pool / RLS context differs.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO public.company_user_roles (company_user_id, role_id, assigned_by)
                    VALUES (:userId, :roleId, :assignedBy)
                    ON CONFLICT (company_user_id, role_id) DO NOTHING
                    """,
            nativeQuery = true)
    void insertRow(@Param("userId") UUID userId, @Param("roleId") UUID roleId, @Param("assignedBy") UUID assignedBy);

    /**
     * Every role id assigned to a given user, system-side
     * (BYPASSRLS). Used by {@code RefreshTokenService.rotate} (F1)
     * which runs on the system pool and resolves the user's role(s)
     * for the JWT claim.
     *
     * <p>Today this returns one element for every user (multi-role
     * comes in PR-2/3 with the JWT {@code roles[]} claim). Kept as
     * a list so the F1 caller can already iterate without an API
     * break; the PR-3 controller swaps the call site to pass the
     * full list as the {@code roles} JWT claim.
     */
    @Query(value = "SELECT role_id FROM public.company_user_roles WHERE company_user_id = :userId", nativeQuery = true)
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);
}
