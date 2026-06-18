package ar.com.logistics.auth.service;

import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.common.exception.BusinessRuleException;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Single chokepoint for the three "non-negotiable" business rules
 * enforced by {@code CompanyUsersService}. Lives here (not in the
 * service) so each rule is unit-testable in isolation with 100%
 * coverage target per spec §3.2.
 *
 * <p>Rules enforced:
 * <ul>
 *   <li><b>First admin</b> (spec §A.5 + C4): the registration-time
 *       admin has {@code created_by IS NULL AND deleted_at IS NULL}.
 *       Their {@code email}, {@code username}, and
 *       {@code COMPANY_ADMIN} role are immutable; their
 *       {@code firstName} / {@code lastName} are NOT.</li>
 *   <li><b>Last admin</b> (spec §A.6 + C5): the tenant must always
 *       retain ≥ 1 active {@code COMPANY_ADMIN}. A mutation that
 *       would drop the count to 0 is rejected.</li>
 *   <li><b>Self edit / disable</b> (spec C6): admins cannot PATCH or
 *       disable their own row. The error surfaces as
 *       {@code SELF_EDIT_BLOCKED} in both cases.</li>
 * </ul>
 *
 * <p>Race-condition note: the {@code lastAdmin} check + the
 * mutation run in the same {@code @Transactional} boundary inside
 * {@link CompanyUsersService}, on the same JDBC connection (RLS
 * GUC set, then count + write). See {@code CompanyUsersService}
 * for the {@code SELECT ... FOR UPDATE} on the tenant row that
 * serialises concurrent admin demotions.
 */
@Component
public class BusinessRuleValidator {

    /** Canonical wire-format codes (PR-3 wires {@code GlobalExceptionHandler} to map these to HTTP statuses). */
    static final String CODE_SELF_EDIT_BLOCKED = "SELF_EDIT_BLOCKED";

    static final String CODE_FIRST_ADMIN_PROTECTED = "FIRST_ADMIN_PROTECTED";
    static final String CODE_LAST_ADMIN_PROTECTED = "LAST_ADMIN_PROTECTED";

    private final CompanyUserRepository userRepository;
    private final CompanyUserRoleRepository roleAssignmentRepository;

    public BusinessRuleValidator(
            CompanyUserRepository userRepository, CompanyUserRoleRepository roleAssignmentRepository) {
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
    }

    // -------------------------------------------------------------------
    //  Read-only predicates
    // -------------------------------------------------------------------

    /**
     * Spec §A.5: the first admin of the tenant is the row whose
     * {@code created_by IS NULL AND deleted_at IS NULL}. Returns
     * {@code true} iff the row exists and satisfies both clauses.
     * The {@code tenantId} parameter is currently unused (the
     * derivation is per-user), but kept on the signature so the
     * future v2 variant — "first admin per tenant, scoped" — can be
     * added without breaking callers.
     */
    public boolean isFirstAdmin(UUID userId, UUID tenantId) {
        return userRepository.countByIdAndCreatedByIsNullAndDeletedAtIsNull(userId) > 0;
    }

    /**
     * Spec §A.6: the user is the last active {@code COMPANY_ADMIN} in
     * the tenant. Returns {@code true} iff the active-admin count for
     * the tenant is exactly 1.
     *
     * <p>Used in conjunction with a separate "is this user actually a
     * {@code COMPANY_ADMIN}" probe by the caller — this method alone
     * cannot distinguish "last admin who happens to be a non-admin
     * right now" from "last admin whose role we're about to remove".
     * {@link CompanyUsersService} composes both checks.
     */
    public boolean isLastAdmin(UUID userId, UUID tenantId) {
        return roleAssignmentRepository.countActiveCompanyAdmins(tenantId) == 1L;
    }

    /**
     * Pure helper: returns {@code true} iff {@code currentUserId}
     * equals {@code targetUserId}. Used by callers that want the
     * check but do not want to throw (e.g. they want to render a
     * disabled button instead).
     */
    public boolean isSelf(UUID currentUserId, UUID targetUserId) {
        return currentUserId.equals(targetUserId);
    }

    // -------------------------------------------------------------------
    //  Throwing assertions (the public API consumed by CompanyUsersService)
    // -------------------------------------------------------------------

    /**
     * Spec C6: an admin cannot edit / disable themselves through the
     * company-users endpoints. Throws {@link BusinessRuleException}
     * with code {@code SELF_EDIT_BLOCKED}.
     */
    public void assertNotSelf(UUID currentUserId, UUID targetUserId) {
        if (isSelf(currentUserId, targetUserId)) {
            throw new BusinessRuleException(CODE_SELF_EDIT_BLOCKED);
        }
    }

    /**
     * Spec C4: the first admin of the tenant is protected from
     * {@link Action#ROLE_EDIT} (cannot have their COMPANY_ADMIN role
     * removed) and {@link Action#DISABLE} (cannot be deactivated).
     * {@link Action#FIELD_EDIT} is ALWAYS allowed for the first admin
     * (firstName / lastName ARE editable per decision #8).
     */
    public void assertNotFirstAdmin(UUID userId, UUID tenantId, Action action) {
        if (action == Action.FIELD_EDIT) {
            // First-admin's firstName / lastName are editable per
            // decision #8; no check needed.
            return;
        }
        if (isFirstAdmin(userId, tenantId)) {
            throw new BusinessRuleException(CODE_FIRST_ADMIN_PROTECTED);
        }
    }

    /**
     * Spec §A.6 + C5: the tenant must always retain ≥ 1 active
     * COMPANY_ADMIN. A mutation that would drop the count to 0 is
     * rejected. The check covers both {@link Action#ROLE_EDIT}
     * (removing the admin role) and {@link Action#DISABLE}
     * (deactivating the admin user — same end-result for the count).
     */
    public void assertNotLastAdmin(UUID userId, UUID tenantId, Action action) {
        if (action == Action.FIELD_EDIT) {
            return; // field edits don't touch roles / status
        }
        if (isLastAdmin(userId, tenantId)) {
            throw new BusinessRuleException(CODE_LAST_ADMIN_PROTECTED);
        }
    }

    // -------------------------------------------------------------------
    //  Action enum
    // -------------------------------------------------------------------

    /**
     * The class of mutation being attempted. The validator picks
     * which rules apply based on this — e.g. {@code FIELD_EDIT} skips
     * the last-admin check because a firstName change cannot
     * demote the admin.
     */
    public enum Action {
        /** {@code firstName} / {@code lastName} edit. Always safe for first/last admin. */
        FIELD_EDIT,
        /** {@code roleIds} change. Protected by first-admin + last-admin rules. */
        ROLE_EDIT,
        /** Status flip to DISABLED. Protected by first-admin + last-admin rules. */
        DISABLE
    }
}
