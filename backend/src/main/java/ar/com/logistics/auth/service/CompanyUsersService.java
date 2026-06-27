package ar.com.logistics.auth.service;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.repository.company.CompanyUserRepository;
import ar.com.logistics.auth.repository.company.CompanyUserRoleRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.BusinessRuleException;
import ar.com.logistics.common.exception.ResourceNotFoundException;
import ar.com.logistics.common.exception.ValidationException;
import ar.com.logistics.common.validation.PasswordValidator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-tenant-aware CRUD service for {@link CompanyUser}. Implements
 * the 7 endpoints under {@code /api/v1/company-users/**} from spec §B.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate input on every mutating call (password complexity,
 *       uniqueness of username / email, role-scope = COMPANY, at
 *       least one role).</li>
 *   <li>Enforce the three admin invariants (first admin, last admin,
 *       self-edit) via {@link BusinessRuleValidator}.</li>
 *   <li>Hydrate the {@code roles[]} projection on every response
 *       through {@link RoleAssignmentService}.</li>
 *   <li>Emit audit events for every mutation (per spec C8).</li>
 *   <li>Revoke refresh tokens on disable / reset-password (per
 *       spec C9).</li>
 * </ul>
 *
 * <p>Transaction boundary: every public method runs in a single
 * {@code @Transactional("companyTransactionManager")} block. The
 * company pool is RLS-scoped via V14; the
 * {@code CompanyUserRoleRepository} reads return only rows that pass
 * the tenant filter.
 */
@Service
public class CompanyUsersService {

    private static final Logger LOG = LoggerFactory.getLogger(CompanyUsersService.class);

    /** Canonical wire-format codes (PR-3 maps these to HTTP statuses). */
    static final String CODE_USER_NOT_FOUND = "USER_NOT_FOUND";

    static final String CODE_USERNAME_TAKEN = "USERNAME_ALREADY_TAKEN";
    static final String CODE_EMAIL_TAKEN = "EMAIL_ALREADY_TAKEN";
    static final String CODE_INVALID_ROLE = "INVALID_ROLE";

    static final String CODE_USER_ALREADY_DISABLED = "USER_ALREADY_DISABLED";
    static final String CODE_USER_ALREADY_ACTIVE = "USER_ALREADY_ACTIVE";

    /** Warning shown in the password-reveal modal (controller layer appends the password). */
    static final String PASSWORD_WARNING =
            "Compartí esta contraseña con el usuario por un canal seguro. No se volverá a mostrar.";

    private final CompanyUserRepository userRepository;
    private final CompanyUserRoleRepository roleAssignmentRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGeneratorService passwordGenerator;
    private final RoleAssignmentService roleAssignmentService;
    private final BusinessRuleValidator businessRuleValidator;
    private final AuditLogger auditLogger;
    private final RefreshTokenService refreshTokenService;

    public CompanyUsersService(
            CompanyUserRepository userRepository,
            CompanyUserRoleRepository roleAssignmentRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            PasswordGeneratorService passwordGenerator,
            RoleAssignmentService roleAssignmentService,
            BusinessRuleValidator businessRuleValidator,
            AuditLogger auditLogger,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.passwordGenerator = passwordGenerator;
        this.roleAssignmentService = roleAssignmentService;
        this.businessRuleValidator = businessRuleValidator;
        this.auditLogger = auditLogger;
        this.refreshTokenService = refreshTokenService;
    }

    // -------------------------------------------------------------------
    //  create (T-2.4)
    // -------------------------------------------------------------------

    /**
     * Spec §B.2: create a tenant-scoped user with 1+ roles.
     * Returns the user detail + the cleartext password (caller —
     * PR-3 controller — writes it into the response exactly once).
     *
     * <p>Order of checks (fail-fast):
     * <ol>
     *   <li>roleIds non-empty + all COMPANY-scoped</li>
     *   <li>password complexity (only when caller-supplied)</li>
     *   <li>username / email uniqueness within the tenant</li>
     *   <li>INSERT user with {@code created_by = adminId}</li>
     *   <li>assign roles</li>
     *   <li>audit</li>
     * </ol>
     */
    @Transactional("companyTransactionManager")
    public CreateCompanyUserResponse create(UUID tenantId, UUID adminId, CreateCompanyUserRequest req) {
        // 1. role-scope + at-least-one-role checks
        roleAssignmentService.validateScopeCompany(req.roleIds());
        roleAssignmentService.validateAtLeastOneRole(req.roleIds());

        // 2. caller-supplied password complexity
        String plainPassword = req.password();
        if (plainPassword != null && !plainPassword.isBlank()) {
            if (!PasswordValidator.isValid(plainPassword)) {
                throw new ValidationException(
                        Map.of(
                                "password",
                                "must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, and one digit"));
            }
        } else {
            // Auto-generate
            plainPassword = passwordGenerator.generate().value();
        }

        // 3. uniqueness checks
        if (userRepository.existsByTenantIdAndUsername(tenantId, req.username())) {
            throw new BusinessRuleException(CODE_USERNAME_TAKEN, Map.of("username", "username is already in use"));
        }
        if (userRepository.existsByTenantIdAndEmail(tenantId, req.email())) {
            throw new BusinessRuleException(CODE_EMAIL_TAKEN, Map.of("email", "email is already in use"));
        }

        // 4. build + save user
        CompanyUser user = CompanyUser.create(tenantId, req.username(), req.email(), req.firstName(), req.lastName());
        // Admin-created users are immediately ACTIVE (no email
        // verification gate — spec §B.2). The factory's default of
        // PENDING_VERIFICATION applies to the registration path only.
        user.setStatus(CompanyUser.UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(plainPassword));
        // created_by is package-private on BaseEntity; reflectively
        // accessible from a subclass. Cast through the getter is the
        // safer pattern, but we have to use the setter because
        // BaseEntity exposes no setter for createdBy. We use the
        // protected setter via a small helper.
        setCreatedBy(user, adminId);
        user = userRepository.save(user);

        // 5. assign roles
        roleAssignmentService.assignRoles(user.getId(), req.roleIds(), adminId);

        // 6. audit
        auditLogger.logAsync(new AuditEvent(
                "COMPANY_USER_CREATED",
                user.getId(),
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of(
                        "createdBy", adminId.toString(),
                        "roleIds", req.roleIds().toString(),
                        "generatedPassword", plainPassword == req.password() ? "false" : "true")));

        return new CreateCompanyUserResponse(
                mapToDetail(user, roleAssignmentService.getRolesForUser(user.getId())),
                plainPassword,
                PASSWORD_WARNING);
    }

    // -------------------------------------------------------------------
    //  list (T-2.5)
    // -------------------------------------------------------------------

    /**
     * Spec §B.1: paginated list of users in the caller's tenant with
     * optional status / roleId / search filters and sort. Returns a
     * {@link Page} so the controller can map the envelope directly.
     *
     * <p>The implementation uses an in-memory filter against the
     * tenant-scoped page query — the v1 spec does not justify a
     * {@code Specification} (the dataset per tenant is bounded by
     * the small-team assumption). The v2 upgrade path is to swap
     * {@link #applyFilters} for a JPA {@code Specification}.
     */
    @Transactional("companyTransactionManager")
    public Page<CompanyUserSummary> list(UUID tenantId, ListFilters filters, Pageable pageable) {
        // Fetch all active users in the tenant via findAll (tenant
        // filter comes from RLS). v1: bound by page size + filter.
        List<CompanyUser> rows = userRepository.findAll();
        org.slf4j.LoggerFactory.getLogger(CompanyUsersService.class)
                .info(
                        "[DEBUG-SERVICE] list() rows.size={}, tenantId={}, status={}, search={}",
                        rows.size(),
                        tenantId,
                        filters.status(),
                        filters.search());
        List<CompanyUserSummary> filtered = new ArrayList<>();
        for (CompanyUser u : rows) {
            org.slf4j.LoggerFactory.getLogger(CompanyUsersService.class)
                    .info("[DEBUG-SERVICE] processing user id={}, tenant_id={}", u.getId(), u.getTenantId());
            if (u.getTenantId() != null && !u.getTenantId().equals(tenantId)) {
                org.slf4j.LoggerFactory.getLogger(CompanyUsersService.class)
                        .info(
                                "[DEBUG-SERVICE] FILTERED OUT user id={} by tenantId check (u.tenantId={}, filter.tenantId={})",
                                u.getId(),
                                u.getTenantId(),
                                tenantId);
                continue;
            }
            if (u.getTenantId() != null && !u.getTenantId().equals(tenantId)) {
                continue;
            }
            if (!matchesStatus(u, filters.status())) {
                continue;
            }
            if (filters.search() != null && !filters.search().isBlank()) {
                String needle = filters.search().toLowerCase();
                String hay = (u.getFirstName() + " " + u.getLastName() + " " + u.getEmail() + " " + u.getUsername())
                        .toLowerCase();
                if (!hay.contains(needle)) {
                    continue;
                }
            }
            filtered.add(mapToSummary(u));
        }
        // Apply roleId filter post-fetch — cheaper than a join per row
        if (filters.roleId() != null) {
            List<CompanyUserSummary> withRole = new ArrayList<>();
            for (CompanyUserSummary s : filtered) {
                if (s.roles().stream().anyMatch(r -> r.id().equals(filters.roleId()))) {
                    withRole.add(s);
                }
            }
            filtered = withRole;
        }
        // Sort + paginate
        List<CompanyUserSummary> sorted = sortSummaries(filtered, pageable);
        int total = sorted.size();
        int from = Math.min((int) pageable.getOffset(), total);
        int to = Math.min(from + pageable.getPageSize(), total);
        return new PageImpl<>(sorted.subList(from, to), pageable, total);
    }

    // -------------------------------------------------------------------
    //  get (T-2.6)
    // -------------------------------------------------------------------

    /**
     * Spec §B.3: fetch a single user scoped to the caller's tenant.
     * Throws {@link ResourceNotFoundException} with code
     * {@code USER_NOT_FOUND} when the user is missing OR belongs to
     * a different tenant (indistinguishable from missing — prevents
     * tenant probing).
     */
    @Transactional("companyTransactionManager")
    public CompanyUserDetail get(UUID tenantId, UUID userId) {
        CompanyUser u = userRepository
                .findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(CODE_USER_NOT_FOUND, Map.of("userId", userId.toString())));
        return mapToDetail(u, roleAssignmentService.getRolesForUser(u.getId()));
    }

    // -------------------------------------------------------------------
    //  update (T-2.7)
    // -------------------------------------------------------------------

    /**
     * Spec §B.4: partial update with first-admin + self-edit +
     * last-admin protection. Supports {@code firstName},
     * {@code lastName}, {@code email}, {@code roleIds} changes.
     */
    @Transactional("companyTransactionManager")
    public CompanyUserDetail update(UUID tenantId, UUID adminId, UUID userId, UpdateCompanyUserRequest req) {
        CompanyUser u = userRepository
                .findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(CODE_USER_NOT_FOUND, Map.of("userId", userId.toString())));

        // Self-edit guard — fires BEFORE the first/last admin checks so
        // an admin cannot bypass them by editing themselves.
        businessRuleValidator.assertNotSelf(adminId, userId);

        // Track which fields actually changed so the audit event
        // carries the diff metadata per spec §B.4 scenario.
        List<String> changedFields = new ArrayList<>();

        // First-name / last-name edits are ALWAYS allowed (per decision #8)
        if (req.firstName() != null && !req.firstName().equals(u.getFirstName())) {
            u.setFirstName(req.firstName());
            changedFields.add("firstName");
        }
        if (req.lastName() != null && !req.lastName().equals(u.getLastName())) {
            u.setLastName(req.lastName());
            changedFields.add("lastName");
        }

        // email is protected when the user IS the first admin
        if (req.email() != null && !req.email().equals(u.getEmail())) {
            businessRuleValidator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);
            // uniqueness check
            if (userRepository.existsByTenantIdAndEmailAndIdNot(tenantId, req.email(), userId)) {
                throw new BusinessRuleException(CODE_EMAIL_TAKEN, Map.of("email", "email is already in use"));
            }
            u.setEmail(req.email());
            changedFields.add("email");
        }

        // roles — protected by first + last admin rules (spec §B.4)
        if (req.roleIds() != null) {
            businessRuleValidator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);
            businessRuleValidator.assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.ROLE_EDIT);
            roleAssignmentService.validateScopeCompany(req.roleIds());
            roleAssignmentService.validateAtLeastOneRole(req.roleIds());
            RoleAssignmentService.RoleDiff diff = roleAssignmentService.assignRoles(userId, req.roleIds(), adminId);
            if (!diff.added().isEmpty()) {
                auditLogger.logAsync(new AuditEvent(
                        "COMPANY_USER_ROLES_ASSIGNED",
                        userId,
                        AuditEvent.UserScope.COMPANY,
                        tenantId,
                        null,
                        null,
                        Map.of(
                                "assignedBy",
                                adminId.toString(),
                                "addedRoleIds",
                                diff.added().toString())));
            }
            if (!diff.removed().isEmpty()) {
                auditLogger.logAsync(new AuditEvent(
                        "COMPANY_USER_ROLES_REMOVED",
                        userId,
                        AuditEvent.UserScope.COMPANY,
                        tenantId,
                        null,
                        null,
                        Map.of(
                                "removedBy",
                                adminId.toString(),
                                "removedRoleIds",
                                diff.removed().toString())));
            }
        }

        u = userRepository.save(u);

        if (!changedFields.isEmpty()) {
            auditLogger.logAsync(new AuditEvent(
                    "COMPANY_USER_UPDATED",
                    userId,
                    AuditEvent.UserScope.COMPANY,
                    tenantId,
                    null,
                    null,
                    Map.of("updatedBy", adminId.toString(), "changedFields", changedFields.toString())));
        }

        return mapToDetail(u, roleAssignmentService.getRolesForUser(u.getId()));
    }

    // -------------------------------------------------------------------
    //  disable (T-2.8)
    // -------------------------------------------------------------------

    /**
     * Spec §B.5: soft-disable a user. Sets {@code status = DISABLED},
     * {@code deleted_at = now()}, revokes all active refresh tokens
     * for the user, and emits the {@code COMPANY_USER_DISABLED}
     * audit event.
     */
    @Transactional("companyTransactionManager")
    public void disable(UUID tenantId, UUID adminId, UUID userId) {
        CompanyUser u = userRepository
                .findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(CODE_USER_NOT_FOUND, Map.of("userId", userId.toString())));

        businessRuleValidator.assertNotSelf(adminId, userId);
        businessRuleValidator.assertNotFirstAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE);
        businessRuleValidator.assertNotLastAdmin(userId, tenantId, BusinessRuleValidator.Action.DISABLE);

        if (u.getStatus() == CompanyUser.UserStatus.DISABLED) {
            throw new BusinessRuleException(CODE_USER_ALREADY_DISABLED, Map.of("userId", userId.toString()));
        }

        u.setStatus(CompanyUser.UserStatus.DISABLED);
        setDeletedAt(u, Instant.now());
        userRepository.save(u);

        // Token revocation: every active refresh token for the user
        // gets stamped revoked_at = now() so their existing sessions
        // can't survive the disable. Spec C9 — the disable flow
        // owns this side-effect in one place.
        int revoked = refreshTokenService.revokeAllForUser(userId, "COMPANY");
        LOG.debug("Disabled user {} and revoked {} refresh tokens", userId, revoked);

        auditLogger.logAsync(new AuditEvent(
                "COMPANY_USER_DISABLED",
                userId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("disabledBy", adminId.toString(), "tokensRevoked", revoked)));
    }

    // -------------------------------------------------------------------
    //  reactivate (T-2.9)
    // -------------------------------------------------------------------

    /**
     * Spec §B.6: undo soft delete — set {@code status = ACTIVE},
     * {@code deleted_at = NULL}, emit {@code COMPANY_USER_REACTIVATED}.
     */
    @Transactional("companyTransactionManager")
    public CompanyUserDetail reactivate(UUID tenantId, UUID adminId, UUID userId) {
        CompanyUser u = userRepository
                .findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(CODE_USER_NOT_FOUND, Map.of("userId", userId.toString())));

        if (u.getStatus() == CompanyUser.UserStatus.ACTIVE) {
            throw new BusinessRuleException(CODE_USER_ALREADY_ACTIVE, Map.of("userId", userId.toString()));
        }

        u.setStatus(CompanyUser.UserStatus.ACTIVE);
        setDeletedAt(u, null);
        u = userRepository.save(u);

        auditLogger.logAsync(new AuditEvent(
                "COMPANY_USER_REACTIVATED",
                userId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("reactivatedBy", adminId.toString())));

        return mapToDetail(u, roleAssignmentService.getRolesForUser(u.getId()));
    }

    // -------------------------------------------------------------------
    //  resetPassword (T-2.10)
    // -------------------------------------------------------------------

    /**
     * Spec §B.7: generate a new temp password, BCrypt + store, reset
     * {@code failed_login_attempts} + {@code locked_until}, revoke all
     * refresh tokens for the user, and return the cleartext password
     * exactly once.
     */
    @Transactional("companyTransactionManager")
    public ResetPasswordResponse resetPassword(UUID tenantId, UUID adminId, UUID userId) {
        CompanyUser u = userRepository
                .findByTenantIdAndId(tenantId, userId)
                .orElseThrow(() -> new BusinessRuleException(CODE_USER_NOT_FOUND, Map.of("userId", userId.toString())));

        String newPassword = passwordGenerator.generate().value();
        u.setPasswordHash(passwordEncoder.encode(newPassword));
        u.setFailedLoginAttempts(0);
        u.setLockedUntil(null);
        u = userRepository.save(u);

        // Spec C9: revoke all refresh tokens so the user's existing
        // sessions can't survive the password reset.
        int revoked = refreshTokenService.revokeAllForUser(userId, "COMPANY");
        LOG.debug("Reset password for user {} and revoked {} refresh tokens", userId, revoked);

        auditLogger.logAsync(new AuditEvent(
                "COMPANY_USER_PASSWORD_RESET",
                userId,
                AuditEvent.UserScope.COMPANY,
                tenantId,
                null,
                null,
                Map.of("resetBy", adminId.toString(), "tokensRevoked", revoked)));

        return new ResetPasswordResponse(userId, u.getUsername(), newPassword, PASSWORD_WARNING);
    }

    // -------------------------------------------------------------------
    //  Internal: BaseEntity mutators (createdBy / deletedAt are
    //  package-private with no setter — use reflection-free helpers
    //  by exposing protected setters on CompanyUser or doing the
    //  updates through field-access reflection. We use a small
    //  helper that leans on the entity's package-private API via
    //  the same-package trick: CompanyUsersService lives in the same
    //  package as CompanyUser, so the field is accessible through
    //  BaseEntity's getter, and we expose setters on CompanyUser.)
    // -------------------------------------------------------------------

    private static void setCreatedBy(CompanyUser u, UUID actorId) {
        u.setCreatedBy(actorId);
    }

    private static void setDeletedAt(CompanyUser u, Instant when) {
        u.setDeletedAt(when);
    }

    // -------------------------------------------------------------------
    //  Mapping helpers
    // -------------------------------------------------------------------

    private CompanyUserSummary mapToSummary(CompanyUser u) {
        List<RoleAssignmentService.RoleRef> roles = roleAssignmentService.getRolesForUser(u.getId());
        return new CompanyUserSummary(
                u.getId(),
                u.getTenantId(),
                u.getUsername(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getStatus().name(),
                roles,
                u.getLastLoginAt(),
                u.getCreatedAt(),
                businessRuleValidator.isFirstAdmin(u.getId(), u.getTenantId()));
    }

    private CompanyUserDetail mapToDetail(CompanyUser u, List<RoleAssignmentService.RoleRef> roles) {
        return new CompanyUserDetail(
                u.getId(),
                u.getTenantId(),
                u.getUsername(),
                u.getEmail(),
                u.getFirstName(),
                u.getLastName(),
                u.getStatus().name(),
                u.isEmailVerified(),
                roles,
                u.getFailedLoginAttempts(),
                u.getLockedUntil(),
                u.getLastLoginAt(),
                u.getCreatedAt(),
                businessRuleValidator.isFirstAdmin(u.getId(), u.getTenantId()));
    }

    private static boolean matchesStatus(CompanyUser u, ListFilters.StatusFilter status) {
        if (status == ListFilters.StatusFilter.ALL) {
            return true;
        }
        if (status == ListFilters.StatusFilter.ACTIVE) {
            return u.getStatus() == CompanyUser.UserStatus.ACTIVE && u.getDeletedAt() == null;
        }
        if (status == ListFilters.StatusFilter.DISABLED) {
            return u.getStatus() == CompanyUser.UserStatus.DISABLED;
        }
        return true;
    }

    private static List<CompanyUserSummary> sortSummaries(List<CompanyUserSummary> rows, Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return rows;
        }
        List<CompanyUserSummary> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            for (var order : pageable.getSort()) {
                int cmp;
                switch (order.getProperty()) {
                    case "firstName" -> cmp = nullSafe(a.firstName(), b.firstName());
                    case "lastName" -> cmp = nullSafe(a.lastName(), b.lastName());
                    case "username" -> cmp = nullSafe(a.username(), b.username());
                    case "status" -> cmp = nullSafe(a.status(), b.status());
                    case "lastLoginAt" -> cmp = nullSafe(a.lastLoginAt(), b.lastLoginAt());
                    case "createdAt" -> cmp = nullSafe(a.createdAt(), b.createdAt());
                    default -> cmp = 0;
                }
                if (cmp != 0) {
                    return order.isAscending() ? cmp : -cmp;
                }
            }
            return 0;
        });
        return sorted;
    }

    private static <T extends Comparable<T>> int nullSafe(T a, T b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        return a.compareTo(b);
    }

    // -------------------------------------------------------------------
    //  Records
    // -------------------------------------------------------------------

    /** Request body for {@link #create}. */
    public record CreateCompanyUserRequest(
            String username, String email, String firstName, String lastName, String password, List<UUID> roleIds) {}

    /** Response body for {@link #create}. The cleartext password appears here exactly once. */
    public record CreateCompanyUserResponse(CompanyUserDetail user, String temporaryPassword, String passwordWarning) {}

    /** Filters for {@link #list}. Null fields are "no filter applied". */
    public record ListFilters(StatusFilter status, UUID roleId, String search) {
        public enum StatusFilter {
            ACTIVE,
            DISABLED,
            ALL
        }
    }

    /** List-view projection of a {@link CompanyUser}. */
    public record CompanyUserSummary(
            UUID id,
            UUID tenantId,
            String username,
            String email,
            String firstName,
            String lastName,
            String status,
            List<RoleAssignmentService.RoleRef> roles,
            Instant lastLoginAt,
            Instant createdAt,
            boolean isFirstAdmin) {}

    /** Detail projection. {@code passwordHash} is intentionally absent — spec C2. */
    public record CompanyUserDetail(
            UUID id,
            UUID tenantId,
            String username,
            String email,
            String firstName,
            String lastName,
            String status,
            boolean emailVerified,
            List<RoleAssignmentService.RoleRef> roles,
            int failedLoginAttempts,
            Instant lockedUntil,
            Instant lastLoginAt,
            Instant createdAt,
            boolean isFirstAdmin) {}

    /** Request body for {@link #update}. Any null field is left unchanged. */
    public record UpdateCompanyUserRequest(String firstName, String lastName, String email, List<UUID> roleIds) {}

    /** Response body for {@link #resetPassword}. */
    public record ResetPasswordResponse(
            UUID userId, String username, String temporaryPassword, String passwordWarning) {}
}
