package ar.com.logistics.auth.repository.company;

import ar.com.logistics.auth.domain.CompanyUserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Company-side repository for the {@code company_user_roles} junction
 * introduced in V12. Bound to the {@code companyDataSource} EMF by
 * {@link ar.com.logistics.config.CompanyJpaConfig}. Reads are
 * RLS-scoped via V14 (the policy resolves {@code tenant_id} through a
 * subquery on {@code company_users}); the {@code RlsAspect} pointcut
 * extension that emits {@code SET LOCAL app.current_tenant} on these
 * calls lands in PR-3. Until then, every method on this interface is
 * reachable through the company pool but unused by application code
 * (the only writer in PR-1 is {@code RegistrationService} on the
 * system pool, which uses the {@code system}-side path described
 * below).
 *
 * <p>Inserts go through {@link #insertRow(UUID, UUID, UUID)} (native
 * SQL) instead of {@link JpaRepository#save(Object)} because
 * Hibernate's {@code @IdClass} handling emits a redundant
 * {@code SELECT} per {@code save} (to materialize the IdClass
 * composite key) and trips on the {@code assigned_at} column's
 * {@code DEFAULT NOW()} when {@code insertable=false} is in play.
 * The native INSERT sidesteps both: the DB fills {@code assigned_at}
 * and there is no preload round-trip. Mirrors the precedent set by
 * {@link ar.com.logistics.auth.repository.system.RefreshTokenAdminRepository#insertRow}.
 */
@Repository
public interface CompanyUserRoleRepository extends JpaRepository<CompanyUserRole, CompanyUserRole.CompanyUserRoleId> {

    /**
     * Insert one junction row, letting the database fill
     * {@code assigned_at} via its {@code DEFAULT NOW()} and optionally
     * recording the actor in {@code assigned_by}. The
     * {@code ON CONFLICT DO NOTHING} makes this safe to call from a
     * registration path that may retry after a partial failure: the
     * second call is a no-op rather than a unique-constraint
     * violation.
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
     * Every role id assigned to a given user. Used by the service
     * layer to hydrate {@code roles[]} on response DTOs without paying
     * the cost of a JPA-managed {@code @OneToMany} association (see
     * {@code CompanyUserRole} javadoc for the rationale). The native
     * projection skips the JPA hydration round-trip and returns raw
     * UUIDs that the caller resolves to {@code [{id, name}]} via a
     * single {@code roles} lookup.
     */
    @Query(value = "SELECT role_id FROM public.company_user_roles WHERE company_user_id = :userId", nativeQuery = true)
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

    Optional<CompanyUserRole> findByCompanyUserIdAndRoleId(UUID companyUserId, UUID roleId);

    /**
     * Delete one (user, role) pair. Used by the role-diff in
     * {@code RoleAssignmentService.removeRoles} (PR-2). Returns the
     * number of rows removed (0 if the pair was not present, 1 if it
     * was) so the caller can decide whether to emit
     * {@code COMPANY_USER_ROLES_REMOVED} for the audit log.
     */
    long deleteByCompanyUserIdAndRoleId(UUID companyUserId, UUID roleId);
}
