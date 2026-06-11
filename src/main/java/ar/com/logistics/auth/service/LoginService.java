package ar.com.logistics.auth.service;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.domain.CompanyUser.UserStatus;
import ar.com.logistics.auth.domain.Role;
import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.auth.repository.system.CompanyUserAdminRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.AuthenticationException;
import ar.com.logistics.common.exception.ErrorCode;
import ar.com.logistics.platform.repository.TenantAdminRepository;
import ar.com.logistics.tenant.domain.Tenant;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the company-user login flow for PR4a (and will be
 * extended in PR4b with refresh + reuse detection).
 *
 * <p>Validates the {@code (slug, username, password)} triple against
 * the system-side DataSource (BYPASSRLS), enforces status +
 * lockout, issues a fresh {@code access_token} JWT, writes the
 * success / failure audit event, and returns a {@link LoginResult}
 * the controller can hand back as JSON + the access_token cookie.
 *
 * <p>For PR4a the refresh-token persistence is deferred to PR4b.
 * The {@code LoginResult.refreshTokenId} and
 * {@code refreshTokenValue} fields carry the raw UUIDs the
 * controller layer will eventually write to the refresh cookie;
 * PR4b will add the {@code refresh_tokens} row insertion (BCrypt
 * hash) and chain-tracking without changing the public signature
 * of this method or the result record.
 *
 * <p>Login side effects on success:
 * <ul>
 *   <li>{@code failed_login_attempts} reset to 0
 *   <li>{@code last_login_at} set to now
 *   <li>{@code locked_until} cleared
 *   <li>{@code audit_log} row with
 *       {@code event_type = 'USER_LOGIN_SUCCESS'}
 * </ul>
 *
 * <p>Login side effects on failure:
 * <ul>
 *   <li>{@code failed_login_attempts} incremented by 1 (unless the
 *       account is currently locked — locked accounts do NOT
 *       re-arm the timer, per spec scenario "Locked account cannot
 *       log in with valid credentials")
 *   <li>on the 5th failure the account is locked for 30 minutes
 *   <li>{@code audit_log} row with
 *       {@code event_type = 'USER_LOGIN_FAILED'} and a
 *       {@code metadata.reason}
 * </ul>
 */
@Service
public class LoginService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final Duration LOCKOUT_DURATION = Duration.ofMinutes(30);
    private static final long ACCESS_TTL_SECONDS = 900L;

    private final TenantAdminRepository tenantAdminRepository;
    private final CompanyUserAdminRepository userAdminRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;
    private final RefreshTokenService refreshTokenService;

    public LoginService(
            TenantAdminRepository tenantAdminRepository,
            CompanyUserAdminRepository userAdminRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogger auditLogger,
            RefreshTokenService refreshTokenService) {
        this.tenantAdminRepository = tenantAdminRepository;
        this.userAdminRepository = userAdminRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
        this.refreshTokenService = refreshTokenService;
    }

    /**
     * Authenticate a company user. Throws
     * {@link AuthenticationException} on any failure path; the
     * controller advice turns it into the canonical error envelope.
     *
     * @param slug tenant slug from the request body
     * @param username username from the request body
     * @param rawPassword plaintext password from the request body
     * @return the {@link LoginResult} the controller serializes
     */
    @Transactional
    public LoginResult login(String slug, String username, String rawPassword) {
        // 1. Resolve the tenant. If it does not exist we MUST treat
        //    the result as INVALID_CREDENTIALS — the spec says the
        //    response MUST NOT reveal which of the three fields was
        //    wrong.
        Optional<Tenant> maybeTenant = tenantAdminRepository.findBySlug(slug);
        if (maybeTenant.isEmpty()) {
            auditLogger.logAsync(loginFailure(null, slug, username, "TENANT_NOT_FOUND"));
            throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS);
        }
        Tenant tenant = maybeTenant.get();

        // 2. Resolve the user. Same generic-error rule.
        Optional<CompanyUser> maybeUser = userAdminRepository.findByTenantIdAndUsername(tenant.getId(), username);
        if (maybeUser.isEmpty()) {
            auditLogger.logAsync(loginFailure(tenant.getId(), slug, username, "USER_NOT_FOUND"));
            throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS);
        }
        CompanyUser user = maybeUser.get();

        // 3. Account disabled: surfaces the canonical 403
        //    ACCOUNT_DISABLED. Per spec, this is the only path that
        //    distinguishes itself from the generic "wrong
        //    credentials" path.
        if (user.getStatus() == UserStatus.DISABLED) {
            auditLogger.logAsync(loginFailure(tenant.getId(), slug, username, "ACCOUNT_DISABLED"));
            throw new AuthenticationException(ErrorCode.ACCOUNT_DISABLED);
        }

        // 4. Lockout: a locked account returns 403 ACCOUNT_LOCKED
        //    with details.retryAfterSeconds = (locked_until - now).
        //    We do NOT increment failed_login_attempts here —
        //    locked accounts do not re-arm the timer.
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(Instant.now())) {
            long retryAfter = Math.max(
                    1, Duration.between(Instant.now(), user.getLockedUntil()).toSeconds());
            auditLogger.logAsync(loginFailure(tenant.getId(), slug, username, "ACCOUNT_LOCKED"));
            throw new AuthenticationException(ErrorCode.ACCOUNT_LOCKED, Map.of("retryAfterSeconds", retryAfter));
        }

        // 5. Verify the password. Wrong password → 401
        //    INVALID_CREDENTIALS with the failure counter
        //    incremented. On the 5th failure the spec mandates a
        //    30-min lock — we flip the state here so the next
        //    attempt sees ACCOUNT_LOCKED.
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            int newAttempts = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(newAttempts);
            if (newAttempts >= MAX_FAILED_ATTEMPTS) {
                user.setLockedUntil(Instant.now().plus(LOCKOUT_DURATION));
                auditLogger.logAsync(new AuditEvent(
                        "ACCOUNT_LOCKED",
                        user.getId(),
                        AuditEvent.UserScope.COMPANY,
                        tenant.getId(),
                        null,
                        null,
                        Map.of("lockedUntil", user.getLockedUntil().toString())));
            }
            userAdminRepository.save(user);
            auditLogger.logAsync(loginFailure(tenant.getId(), slug, username, "INVALID_PASSWORD"));
            throw new AuthenticationException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 6. Load the role. The CompanyUser holds role_id; we read
        //    the name (e.g. COMPANY_ADMIN) from public.roles.
        Role role = roleRepository
                .findById(user.getRoleId())
                .orElseThrow(() -> new IllegalStateException(
                        "company_users row " + user.getId() + " references a non-existent role " + user.getRoleId()));

        // 7. Success side-effects: reset counter, clear lock,
        //    stamp last_login_at.
        if (user.getFailedLoginAttempts() != 0) {
            user.setFailedLoginAttempts(0);
        }
        if (user.getLockedUntil() != null) {
            user.setLockedUntil(null);
        }
        user.setLastLoginAt(Instant.now());
        userAdminRepository.save(user);

        // 8. Issue access token.
        String accessToken =
                jwtService.issueCompanyToken(user.getId(), tenant.getId(), tenant.getSlug(), role.getName());

        // 9. Audit success.
        auditLogger.logAsync(new AuditEvent(
                "USER_LOGIN_SUCCESS",
                user.getId(),
                AuditEvent.UserScope.COMPANY,
                tenant.getId(),
                null,
                null,
                Map.of("loginSlug", tenant.getSlug())));

        // 10. Issue a refresh token row. The controller will
        //     write the raw UUID to the refresh_token cookie
        //     and the row itself carries the BCrypt hash.
        RefreshTokenService.Issued refresh = refreshTokenService.issue(user, tenant, role);

        return new LoginResult(
                accessToken,
                refresh.refreshTokenId(),
                refresh.refreshTokenValue(),
                refresh.refreshTokenExpiresAt(),
                user,
                tenant,
                role,
                ACCESS_TTL_SECONDS);
    }

    private static AuditEvent loginFailure(UUID tenantId, String slug, String username, String reason) {
        return new AuditEvent(
                "USER_LOGIN_FAILED",
                null,
                AuditEvent.UserScope.ANONYMOUS,
                tenantId,
                null,
                null,
                Map.of("slug", slug, "username", username, "reason", reason));
    }

    /**
     * Read-only projection of the login result. The controller
     * translates this into JSON (sans refresh fields) + the
     * access_token cookie.
     *
     * <p>PR4a returns null refresh fields because the refresh
     * persistence lands with the RefreshToken service in PR4b.
     */
    public record LoginResult(
            String accessToken,
            UUID refreshTokenId,
            UUID refreshTokenValue,
            Instant refreshTokenExpiresAt,
            CompanyUser user,
            Tenant tenant,
            Role role,
            long expiresIn) {}
}
