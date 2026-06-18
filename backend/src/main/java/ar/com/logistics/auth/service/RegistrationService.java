package ar.com.logistics.auth.service;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.domain.Role;
import ar.com.logistics.auth.dto.RegisterRequest;
import ar.com.logistics.auth.dto.RegisterResponse;
import ar.com.logistics.auth.repository.system.CompanyUserAdminRepository;
import ar.com.logistics.auth.repository.system.CompanyUserRoleAdminRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.ErrorCode;
import ar.com.logistics.common.exception.ResourceConflictException;
import ar.com.logistics.common.exception.ValidationException;
import ar.com.logistics.common.validation.CuitValidator;
import ar.com.logistics.common.validation.PasswordValidator;
import ar.com.logistics.common.validation.SlugValidator;
import ar.com.logistics.common.validation.UsernameValidator;
import ar.com.logistics.platform.repository.TenantAdminRepository;
import ar.com.logistics.tenant.domain.Tenant;
import ar.com.logistics.tenant.dto.CuitAvailabilityResponse;
import ar.com.logistics.tenant.dto.SlugAvailabilityResponse;
import ar.com.logistics.tenant.dto.UsernameAvailabilityResponse;
import ar.com.logistics.tenant.repository.TenantRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant-registration orchestrator. Handles the full
 * self-service onboarding flow:
 * <ol>
 *   <li>Validate format of slug, CUIT, username, password via the
 *       static validators (SlugValidator, CuitValidator,
 *       UsernameValidator, PasswordValidator). Failures surface as
 *       {@code 400 VALIDATION_ERROR} with per-field details.</li>
 *   <li>Check uniqueness of slug, CUIT, username, email against the
 *       existing rows (system-side, BYPASSRLS). Failures surface as
 *       the appropriate {@code 409} code.</li>
 *   <li>INSERT the {@code tenant} row, then the {@code company_user}
 *       row with the role_id of {@code COMPANY_ADMIN} and a
 *       BCrypt-hashed password.</li>
 *   <li>Generate a UUID verification token + 24h expiry so PR8 (or
 *       later) can implement the email confirmation flow.</li>
 *   <li>Emit a {@code TENANT_REGISTERED} audit event.</li>
 * </ol>
 *
 * <p>The transactional boundary is bound to the
 * {@code systemTransactionManager} so the entire flow runs against
 * the {@code systemDataSource} pool (BYPASSRLS).
 */
@Service
public class RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

    private static final String ROLE_COMPANY_ADMIN = "COMPANY_ADMIN";

    private final TenantAdminRepository tenantAdminRepository;
    private final TenantRepository tenantRepository;
    private final CompanyUserAdminRepository companyUserAdminRepository;
    private final CompanyUserRoleAdminRepository companyUserRoleAdminRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogger auditLogger;

    public RegistrationService(
            TenantAdminRepository tenantAdminRepository,
            TenantRepository tenantRepository,
            CompanyUserAdminRepository companyUserAdminRepository,
            CompanyUserRoleAdminRepository companyUserRoleAdminRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuditLogger auditLogger) {
        this.tenantAdminRepository = tenantAdminRepository;
        this.tenantRepository = tenantRepository;
        this.companyUserAdminRepository = companyUserAdminRepository;
        this.companyUserRoleAdminRepository = companyUserRoleAdminRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogger = auditLogger;
    }

    // ---------- Registration ----------

    /**
     * Register a new tenant + first admin user. Atomic; the entire
     * method runs inside one systemDataSource transaction.
     */
    @Transactional(transactionManager = "systemTransactionManager")
    public RegisterResponse register(RegisterRequest req) {
        // 1. Slug format + reserved
        String slug = req.company().slug();
        if (!SlugValidator.isValidFormat(slug)) {
            throw new ValidationException(
                    Map.of("slug", "must be 2-12 chars, lowercase letters and digits, starting with a letter"));
        }
        if (SlugValidator.isReserved(slug)) {
            throw new ResourceConflictException(
                    ErrorCode.RESERVED_SLUG, Map.of("slug", "slug is reserved and cannot be used"));
        }

        // 2. CUIT format + check-digit
        String cuitRaw = req.company().cuit();
        if (!CuitValidator.isValid(cuitRaw)) {
            throw new ValidationException(Map.of("cuit", "CUIT failed the mod-11 check-digit validation"));
        }
        String cuitNormalized = CuitValidator.normalize(cuitRaw);

        // 3. Username format
        String username = req.admin().username();
        if (!UsernameValidator.isValid(username)) {
            throw new ValidationException(
                    Map.of("username", "must be 3-30 chars, lowercase letters/digits/_/-/., starting with a letter"));
        }

        // 4. Password policy
        if (!PasswordValidator.isValid(req.admin().password())) {
            throw new ValidationException(
                    Map.of(
                            "password",
                            "must be at least 8 characters and contain at least one uppercase letter, one lowercase letter, and one digit"));
        }

        // 5. Uniqueness checks (against systemDataSource, BYPASSRLS)
        if (tenantAdminRepository.existsBySlug(slug)) {
            throw new ResourceConflictException(ErrorCode.SLUG_ALREADY_TAKEN, Map.of("slug", "slug is already in use"));
        }
        if (tenantAdminRepository.existsByCuit(cuitNormalized)) {
            throw new ResourceConflictException(
                    ErrorCode.CUIT_ALREADY_REGISTERED, Map.of("cuit", "CUIT is already registered"));
        }

        // 6. Look up the COMPANY_ADMIN role
        Role role = roleRepository
                .findByNameAndScope(ROLE_COMPANY_ADMIN, Role.RoleScope.COMPANY)
                .orElseThrow(() -> new IllegalStateException(
                        "COMPANY_ADMIN role is missing from the catalog; V7 seed migration did not run"));

        // 7. Build and save the tenant
        Tenant tenant = Tenant.create(
                slug,
                req.company().legalName(),
                req.company().commercialName(),
                cuitNormalized,
                req.company().taxType(),
                req.company().contactEmail(),
                req.company().contactPhone(),
                req.company().address().country(),
                req.company().address().province().name(),
                req.company().address().city(),
                req.company().address().line(),
                req.company().address().number(),
                req.company().address().floor(),
                req.company().address().apartment(),
                req.company().address().postalCode());
        // status is set by Tenant.create to ACTIVE
        tenant = tenantAdminRepository.save(tenant);

        // 8. Defensive double-check (tenant is fresh; should always be empty)
        if (companyUserAdminRepository.existsByTenantIdAndUsername(tenant.getId(), username)) {
            // Should be impossible because the tenant just got its id,
            // but the unique constraint is a stronger guarantee.
            throw new ResourceConflictException(
                    ErrorCode.USERNAME_ALREADY_TAKEN, Map.of("username", "username is already in use"));
        }
        if (companyUserAdminRepository.existsByTenantIdAndEmail(
                tenant.getId(), req.admin().email())) {
            throw new ResourceConflictException(
                    ErrorCode.EMAIL_ALREADY_TAKEN, Map.of("email", "email is already in use"));
        }

        // 9. Build the admin user (verification token + 24h expiry set in the factory)
        CompanyUser user = CompanyUser.create(
                tenant.getId(),
                username,
                req.admin().email(),
                req.admin().firstName(),
                req.admin().lastName());
        user.setPasswordHash(passwordEncoder.encode(req.admin().password()));
        user = companyUserAdminRepository.save(user);

        // 9b. Attach the COMPANY_ADMIN role through the company_user_roles
        // junction (V12). The junction lives on the systemDataSource side
        // because no app.current_tenant context is set yet — the tenant
        // was just created in this same transaction. assignedBy is null
        // because registration has no actor (the isFirstAdmin criterion
        // depends on this: created_by IS NULL).
        companyUserRoleAdminRepository.insertRow(user.getId(), role.getId(), null);

        // 10. Audit
        auditLogger.logAsync(new AuditEvent(
                "TENANT_REGISTERED",
                user.getId(),
                AuditEvent.UserScope.COMPANY,
                tenant.getId(),
                null, // no request context here; passed by the controller in a follow-up if needed
                null,
                Map.of("slug", slug, "legalName", tenant.getLegalName())));

        LOG.info("Registered tenant slug={} tenantId={} userId={}", slug, tenant.getId(), user.getId());

        // 11. Build response
        return new RegisterResponse(
                tenant.getId(),
                tenant.getSlug(),
                tenant.getLegalName(),
                tenant.getCuit(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                ROLE_COMPANY_ADMIN,
                true);
    }

    // ---------- Availability lookups ----------

    /**
     * Slug availability. Returns 200 with the appropriate
     * {@code available} / {@code reason} values per spec lines 590-609.
     * The endpoint is public and has no rate-limit budget attached
     * in v1 (the rate-limit filter is PR4 work).
     */
    public SlugAvailabilityResponse checkSlugAvailability(String slug) {
        if (!SlugValidator.isValidFormat(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "VALIDATION_ERROR");
        }
        if (SlugValidator.isReserved(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "RESERVED_SLUG");
        }
        if (tenantAdminRepository.existsBySlug(slug)) {
            return SlugAvailabilityResponse.unavailable(slug, "SLUG_ALREADY_TAKEN");
        }
        return SlugAvailabilityResponse.available(slug);
    }

    /**
     * CUIT availability. Two booleans: the wizard distinguishes
     * "format is wrong" (must fix input) from "format is right but
     * already taken" (different UX).
     */
    public CuitAvailabilityResponse checkCuitAvailability(String cuit) {
        if (!CuitValidator.isValid(cuit)) {
            return CuitAvailabilityResponse.invalidFormat(cuit);
        }
        String normalized = CuitValidator.normalize(cuit);
        if (tenantAdminRepository.existsByCuit(normalized)) {
            return CuitAvailabilityResponse.validButTaken(cuit);
        }
        return CuitAvailabilityResponse.validAndAvailable(cuit);
    }

    /**
     * Username availability. The lookup is per-tenant, so the slug
     * is resolved first; if the tenant is missing we report
     * {@code SLUG_NOT_FOUND} (per spec lines 302-312) so the wizard
     * can show a slug-side error before re-asking the username.
     */
    public UsernameAvailabilityResponse checkUsernameAvailability(String slug, String username) {
        // 1. Username format (check before the DB query — the slug
        // may not exist yet if the user is in the middle of the
        // register wizard's step 2 typing the username, but the
        // username is still being validated for format).
        if (!UsernameValidator.isValid(username)) {
            return UsernameAvailabilityResponse.unavailable(slug, username, "VALIDATION_ERROR");
        }

        // 2. Tenant must exist. If the slug is not in the DB,
        // return AVAILABLE (not UNAVAILABLE) because there is
        // no existing tenant whose username space could
        // collide with this one. The user is in the middle of
        // the register wizard (the slug they typed in step 1
        // has not been created yet), and the username is
        // trivially available against the (empty) username
        // space of a (non-existent) tenant.
        //
        // The final uniqueness check happens at the /auth/register
        // POST endpoint, which is the only place where we can
        // guarantee atomicity (the slug and the username are
        // committed in the same transaction; if the slug was
        // created concurrently between this check and the POST,
        // the POST will fail with USERNAME_ALREADY_TAKEN).
        //
        // Returning UNAVAILABLE here caused the F1 wizard to
        // falsely show "Este usuario ya existe" for any username
        // typed against a slug that had not yet been registered
        // (i.e. every slug during the wizard's step 2).
        var tenant = tenantAdminRepository.findBySlug(slug);
        if (tenant.isEmpty()) {
            return UsernameAvailabilityResponse.available(slug, username);
        }
        UUID tenantId = tenant.get().getId();

        // 3. Per-tenant uniqueness (case-insensitive per spec)
        boolean taken = companyUserAdminRepository.existsByTenantIdAndUsername(tenantId, username.toLowerCase());
        // Note: the database stores usernames in their original
        // case (the spec says username is lowercase, but the
        // repository check is exact-match. The username validator
        // already rejects uppercase, so this is safe in v1; v2 may
        // add a LOWER() wrapper to harden against mixed-case legacy
        // data).
        if (taken) {
            return UsernameAvailabilityResponse.unavailable(slug, username, "USERNAME_ALREADY_TAKEN");
        }

        return UsernameAvailabilityResponse.available(slug, username);
    }
}
