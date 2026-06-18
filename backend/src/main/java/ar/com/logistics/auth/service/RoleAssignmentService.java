package ar.com.logistics.auth.service;

import ar.com.logistics.auth.domain.Role;
import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Owns the many-to-many junction between {@link
 * ar.com.logistics.auth.domain.CompanyUser} and {@link Role}. Sits
 * between {@link CompanyUsersService} and the
 * {@link CompanyUserRoleRepository}.
 *
 * <p>Three responsibilities:
 * <ol>
 *   <li><b>Bulk assign / remove</b> roles on the junction, with
 *       diff-aware writes so the audit layer can emit the right
 *       {@code _ROLES_ASSIGNED} / {@code _ROLES_REMOVED} events.</li>
 *   <li><b>Validate the role scope</b> (spec C7) — every role id in a
 *       mutation request must resolve to a {@code Role} with
 *       {@code scope = COMPANY}. Platform roles cannot be assigned
 *       from this service.</li>
 *   <li><b>Validate the "at least one role" invariant</b> (spec §B.4)
 *       — removing roles that would leave the user with zero is
 *       rejected with {@code VALIDATION_ERROR}.</li>
 * </ol>
 *
 * <p>This service is the single chokepoint for junction writes; no
 * other service inserts / deletes {@code company_user_roles} rows
 * directly (the registration path uses the {@code system}-side twin
 * {@code CompanyUserRoleAdminRepository}, which mirrors this one's
 * surface).
 */
@Service
public class RoleAssignmentService {

    /** Canonical wire-format code for the spec §B.4 invariant. */
    static final String CODE_AT_LEAST_ONE_ROLE = "VALIDATION_ERROR";

    /** Canonical wire-format code for spec C7 (PLATFORM role rejection). */
    static final String CODE_INVALID_ROLE = "INVALID_ROLE";

    /** Default human-readable message for the at-least-one-role rejection. */
    static final String MSG_AT_LEAST_ONE_ROLE = "A user must have at least one role.";

    /** Default human-readable message for the PLATFORM-scope rejection. */
    static final String MSG_INVALID_ROLE = "One or more role ids are invalid (not COMPANY scope or not found).";

    private final CompanyUserRoleRepository companyUserRoleRepository;
    private final RoleRepository roleRepository;

    public RoleAssignmentService(CompanyUserRoleRepository companyUserRoleRepository, RoleRepository roleRepository) {
        this.companyUserRoleRepository = companyUserRoleRepository;
        this.roleRepository = roleRepository;
    }

    // -------------------------------------------------------------------
    //  Mutations
    // -------------------------------------------------------------------

    /**
     * Insert junction rows for every role id in {@code desiredRoleIds}
     * that is NOT already assigned. Idempotent on the
     * {@code ON CONFLICT DO NOTHING} insert from the repo. Returns
     * the {@link RoleDiff} so the caller can emit per-event audit
     * metadata.
     *
     * @param userId          the target user
     * @param desiredRoleIds  the full desired set (NOT a delta)
     * @param assignedBy      the actor (admin's user id)
     */
    public RoleDiff assignRoles(UUID userId, Collection<UUID> desiredRoleIds, UUID assignedBy) {
        Set<UUID> desired = new LinkedHashSet<>(desiredRoleIds);
        Set<UUID> current = new LinkedHashSet<>(companyUserRoleRepository.findRoleIdsByUserId(userId));

        RoleDiff diff = computeDiff(current, desired);
        for (UUID addedId : diff.added()) {
            companyUserRoleRepository.insertRow(userId, addedId, assignedBy);
        }
        return diff;
    }

    /**
     * Delete junction rows for every role id in {@code roleIdsToRemove}
     * that is currently assigned. Does NOT validate "at least one role"
     * — callers that mutate the full set should use
     * {@link #assignRoles}; this method is the narrow "remove these
     * specific ones" path. Returns the {@link RoleDiff} so the caller
     * can emit per-event audit metadata. The {@code added} field of the
     * returned diff carries the role ids that were NOT removed (i.e.
     * the survivors) so the audit layer can record the post-mutation
     * state.
     */
    public RoleDiff removeRoles(UUID userId, Collection<UUID> roleIdsToRemove) {
        Set<UUID> desiredToRemove = new LinkedHashSet<>(roleIdsToRemove);
        Set<UUID> current = new LinkedHashSet<>(companyUserRoleRepository.findRoleIdsByUserId(userId));

        Set<UUID> removed = new LinkedHashSet<>(current);
        removed.retainAll(desiredToRemove);
        Set<UUID> kept = new LinkedHashSet<>(current);
        kept.removeAll(desiredToRemove);

        for (UUID removedId : removed) {
            companyUserRoleRepository.deleteByCompanyUserIdAndRoleId(userId, removedId);
        }
        return new RoleDiff(List.copyOf(kept), List.copyOf(removed), List.of());
    }

    // -------------------------------------------------------------------
    //  Read-only helpers (pure)
    // -------------------------------------------------------------------

    /**
     * Pure helper: classify each desired role id as {@code added},
     * {@code removed}, or {@code unchanged} relative to the user's
     * current set. Used by {@link CompanyUsersService#update} to
     * decide which audit events to emit.
     */
    public RoleDiff diffRoles(UUID userId, Collection<UUID> desiredRoleIds) {
        Set<UUID> desired = new LinkedHashSet<>(desiredRoleIds);
        Set<UUID> current = new LinkedHashSet<>(companyUserRoleRepository.findRoleIdsByUserId(userId));
        return computeDiff(current, desired);
    }

    /**
     * Fetch the user's roles and project them as {@code [{id, name}]}.
     * Returns an empty list when the user has no roles (no role-catalog
     * lookup happens in that case — saves a query).
     */
    public List<RoleRef> getRolesForUser(UUID userId) {
        List<UUID> roleIds = companyUserRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Role> byId =
                roleRepository.findAllById(roleIds).stream().collect(Collectors.toMap(Role::getId, r -> r));
        List<RoleRef> out = new ArrayList<>(roleIds.size());
        for (UUID id : roleIds) {
            Role r = byId.get(id);
            if (r != null) {
                out.add(new RoleRef(r.getId(), r.getName()));
            }
        }
        return out;
    }

    // -------------------------------------------------------------------
    //  Validators
    // -------------------------------------------------------------------

    /**
     * Spec C7: every role id in a mutation request must resolve to a
     * role with {@code scope = COMPANY}. Throws
     * {@link BusinessRuleException} with code {@code INVALID_ROLE}
     * (and {@code details.invalidRoleIds} listing the offenders) when
     * any id is missing OR has {@code scope = PLATFORM}.
     *
     * <p>Fail-fast: the check runs BEFORE any DB write so a malformed
     * request never leaves a partial state behind.
     */
    public void validateScopeCompany(Collection<UUID> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return; // nothing to validate; the at-least-one check is separate
        }
        Map<UUID, Role> byId =
                roleRepository.findAllById(roleIds).stream().collect(Collectors.toMap(Role::getId, r -> r));

        List<String> invalid = new ArrayList<>();
        for (UUID id : roleIds) {
            Role r = byId.get(id);
            if (r == null || r.getScope() != Role.RoleScope.COMPANY) {
                invalid.add(id.toString());
            }
        }
        if (!invalid.isEmpty()) {
            throw new BusinessRuleException(CODE_INVALID_ROLE, MSG_INVALID_ROLE, Map.of("invalidRoleIds", invalid));
        }
    }

    /**
     * Spec §B.4: a user must always retain at least one role. Throws
     * {@link BusinessRuleException} with code
     * {@code VALIDATION_ERROR} when the desired set is empty.
     */
    public void validateAtLeastOneRole(Collection<UUID> desiredRoleIds) {
        if (desiredRoleIds == null || desiredRoleIds.isEmpty()) {
            throw new BusinessRuleException(CODE_AT_LEAST_ONE_ROLE, MSG_AT_LEAST_ONE_ROLE, null);
        }
    }

    // -------------------------------------------------------------------
    //  Internals
    // -------------------------------------------------------------------

    private RoleDiff computeDiff(Set<UUID> current, Set<UUID> desired) {
        Set<UUID> added = new LinkedHashSet<>(desired);
        added.removeAll(current);
        Set<UUID> removed = new LinkedHashSet<>(current);
        removed.removeAll(desired);
        Set<UUID> unchanged = new LinkedHashSet<>(current);
        unchanged.retainAll(desired);
        return new RoleDiff(List.copyOf(added), List.copyOf(removed), List.copyOf(unchanged));
    }

    // -------------------------------------------------------------------
    //  Records
    // -------------------------------------------------------------------

    /**
     * Read-only projection of the {@code roles[]} field on response
     * DTOs. Equality is by id + name (so the test harness can compare
     * without instabilities from list ordering).
     */
    public record RoleRef(UUID id, String name) {}

    /**
     * Read-only projection of the diff between the user's current role
     * set and a desired set. {@code added} / {@code removed} are the
     * deltas the caller should act on; {@code unchanged} is included
     * so audit events can carry "current total" metadata.
     *
     * <p>Note: when returned from {@link #removeRoles}, the semantics
     * are different — {@code added} carries the survivors and
     * {@code removed} carries what was actually deleted. The field
     * names are kept stable so callers can treat the diff as "what
     * happened to the user's role set during this mutation".
     */
    public record RoleDiff(List<UUID> added, List<UUID> removed, List<UUID> unchanged) {}
}
