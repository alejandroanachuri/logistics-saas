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

    /**
     * Count the active {@code COMPANY_ADMIN} role assignments for a
     * tenant. Used by {@code BusinessRuleValidator.isLastAdmin} (PR-2)
     * to enforce the "tenant must always retain ≥ 1 active admin"
     * invariant (spec A.6 + C5).
     *
     * <p>The query joins through {@code company_users} (filtered by
     * tenant via RLS) and {@code roles} (looking up the
     * {@code COMPANY_ADMIN} role by name + scope). Returns 0 when the
     * tenant has no active admins; the caller interprets count == 1
     * as "this user is the last admin".
     *
     * <p>The SQL is hand-rolled (not a derived query) because the join
     * through three tables with two filter predicates and a JOIN on
     * roles is more readable as native SQL than as a method-name DSL.
     */
    @Query(
            value =
                    """
                    SELECT COUNT(*)
                      FROM public.company_user_roles cur
                      JOIN public.company_users cu ON cur.company_user_id = cu.id
                      JOIN public.roles r ON cur.role_id = r.id
                     WHERE r.name = 'COMPANY_ADMIN'
                       AND r.scope = 'COMPANY'
                       AND cu.tenant_id = :tenantId
                       AND cu.status = 'ACTIVE'
                       AND cu.deleted_at IS NULL
                    """,
            nativeQuery = true)
    long countActiveCompanyAdmins(@Param("tenantId") UUID tenantId);
}
