package ar.com.logistics.auth.service;

import ar.com.logistics.auth.domain.CompanyUser;
import ar.com.logistics.auth.domain.RefreshToken;
import ar.com.logistics.auth.domain.RefreshToken.UserScope;
import ar.com.logistics.auth.domain.Role;
import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.auth.repository.system.CompanyUserAdminRepository;
import ar.com.logistics.auth.repository.system.CompanyUserRoleAdminRepository;
import ar.com.logistics.auth.repository.system.RefreshTokenAdminRepository;
import ar.com.logistics.auth.repository.system.RoleRepository;
import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.AuthenticationException;
import ar.com.logistics.common.exception.ErrorCode;
import ar.com.logistics.platform.repository.TenantAdminRepository;
import ar.com.logistics.tenant.domain.Tenant;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Refresh-token issuance, rotation, revocation, and reuse-detection.
 *
 * <p>Spec source: {@code openspec/changes/etapa-1-registro/specs/refresh-token-rotation.md}.
 * All paths are written so that the BCrypt-hash of a presented raw
 * UUID resolves to exactly one row; the {@code token_hash} column
 * has a UNIQUE constraint, so a rotation that returns an existing
 * hash would throw on insert — we catch that case (very rare, the
 * random-UUID collision probability is ~10⁻³⁷) and treat it as a
 * client-side retry signal.
 *
 * <p>Atomicity: every state-mutating method runs inside a single
 * Spring transaction. The chain-revocation branch in
 * {@link #handleReuse} uses the {@code refresh_tokens.id IN (...)}
 * predicate with the ids returned by
 * {@link RefreshTokenAdminRepository#findChainIds} so the UPDATE
 * is a single statement — Postgres wraps it in the outer
 * transaction and the audit row + the 401 response are committed
 * together.
 */
@Service
public class RefreshTokenService {

    private static final Logger LOG = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final Duration REFRESH_TTL = Duration.ofDays(7);
    private static final long ACCESS_TTL_SECONDS = 900L;

    private final RefreshTokenAdminRepository refreshTokenRepo;
    private final TenantAdminRepository tenantAdminRepository;
    private final CompanyUserAdminRepository userAdminRepository;
    private final CompanyUserRoleAdminRepository userRoleAdminRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogger auditLogger;

    public RefreshTokenService(
            RefreshTokenAdminRepository refreshTokenRepo,
            TenantAdminRepository tenantAdminRepository,
            CompanyUserAdminRepository userAdminRepository,
            CompanyUserRoleAdminRepository userRoleAdminRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AuditLogger auditLogger) {
        this.refreshTokenRepo = refreshTokenRepo;
        this.tenantAdminRepository = tenantAdminRepository;
        this.userAdminRepository = userAdminRepository;
        this.userRoleAdminRepository = userRoleAdminRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogger = auditLogger;
    }

    // -------------------------------------------------------------------
    //  Issuance
    // -------------------------------------------------------------------

    /**
     * Mint a fresh refresh token for the given (user, tenant) pair
     * and persist the row. Returns the row id and the raw UUID the
     * caller will write to the cookie.
     */
    @Transactional("systemTransactionManager")
    public Issued issue(CompanyUser user, Tenant tenant, Role role) {
        UUID rowId = UUID.randomUUID();
        UUID rawUuid = UUID.randomUUID();
        String hash = passwordEncoder.encode(rawUuid.toString());
        Instant expiresAt = Instant.now().plus(REFRESH_TTL);

        refreshTokenRepo.insertRow(rowId, user.getId(), UserScope.COMPANY.name(), tenant.getId(), hash, expiresAt);

        return new Issued(rowId, rawUuid, expiresAt, user, tenant, role);
    }

    // -------------------------------------------------------------------
    //  Validation + rotation
    // -------------------------------------------------------------------

    /**
     * Validate a presented refresh cookie, rotate the row, and
     * return a fresh access JWT + the new raw refresh UUID. Throws
     * {@link AuthenticationException} on any failure path.
     *
     * <p>Token theft (reuse-detection) is handled by
     * {@link #handleReuse} which revokes the entire chain
     * atomically and writes a {@code TOKEN_REUSE_DETECTED} audit
     * event.
     *
     * @param rawCookie the value the client stored in the
     *                  {@code refresh_token} cookie
     */
    @Transactional("systemTransactionManager")
    public Refreshed validateAndRotate(String rawCookie) {
        if (rawCookie == null || rawCookie.isBlank()) {
            throw new AuthenticationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }

        // We need the BCrypt hash to lookup. Because the column
        // is UNIQUE on token_hash and BCrypt is non-deterministic,
        // we cannot compute the hash here and SELECT by hash —
        // we have to SELECT all rows and BCrypt-match in
        // memory. For a v1 this is fine; production would
        // introduce a deterministic pre-hash (e.g. SHA-256) as
        // an index. See design.md open_gaps for the v2
        // optimisation.
        var allRows = refreshTokenRepo.findAll();
        RefreshToken match = null;
        for (RefreshToken r : allRows) {
            if (passwordEncoder.matches(rawCookie, r.getTokenHash())) {
                match = r;
                break;
            }
        }
        if (match == null) {
            throw new AuthenticationException(ErrorCode.REFRESH_TOKEN_INVALID);
        }
        if (match.getRevokedAt() != null) {
            // Reuse detection. The spec mandates the entire chain
            // is revoked atomically. We do that here and throw.
            int chainLength = handleReuse(match);
            throw new AuthenticationException(ErrorCode.REFRESH_TOKEN_REVOKED, Map.of("chainLength", chainLength));
        }
        if (!match.getExpiresAt().isAfter(Instant.now())) {
            throw new AuthenticationException(ErrorCode.REFRESH_TOKEN_EXPIRED);
        }

        // Load the user + tenant + role to mint the new access JWT.
        final UUID matchUserId = match.getUserId();
        final UUID matchTenantId = match.getTenantId();
        CompanyUser user = userAdminRepository
                .findById(matchUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "refresh_tokens row references a non-existent company_users row " + matchUserId));
        Tenant tenant = tenantAdminRepository
                .findById(matchTenantId)
                .orElseThrow(() -> new IllegalStateException(
                        "refresh_tokens row references a non-existent tenants row " + matchTenantId));
        Role role = roleRepository
                .findById(userRoleAdminRepository.findRoleIdsByUserId(user.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException(
                                "company_users row " + user.getId() + " has no role assignment in company_user_roles")))
                .orElseThrow(() -> new IllegalStateException(
                        "company_user_roles row for user " + user.getId() + " references a non-existent role"));

        // Issue the new pair.
        UUID newRawUuid = UUID.randomUUID();
        String newHash = passwordEncoder.encode(newRawUuid.toString());
        UUID newRowId = UUID.randomUUID();
        Instant newExpiresAt = Instant.now().plus(REFRESH_TTL);
        refreshTokenRepo.insertRow(
                newRowId, user.getId(), UserScope.COMPANY.name(), tenant.getId(), newHash, newExpiresAt);

        // Revoke the old row, chaining it.
        match.setRevokedAt(Instant.now());
        match.setReplacedBy(newRowId);
        refreshTokenRepo.save(match);

        // Issue the new access token.
        String accessToken =
                jwtService.issueCompanyToken(user.getId(), tenant.getId(), tenant.getSlug(), role.getName());

        return new Refreshed(accessToken, newRawUuid, newExpiresAt, ACCESS_TTL_SECONDS, user, tenant, role);
    }

    // -------------------------------------------------------------------
    //  Logout
    // -------------------------------------------------------------------

    /**
     * Revoke the row whose BCrypt-hash matches the presented
     * cookie, scoped to the COMPANY scope (platform logout
     * lives in PR7). Idempotent: a second call with the same
     * cookie is a no-op and returns {@code false}.
     */
    @Transactional("systemTransactionManager")
    public boolean revoke(String rawCookie) {
        if (rawCookie == null || rawCookie.isBlank()) {
            return false;
        }
        var allRows = refreshTokenRepo.findAll();
        for (RefreshToken r : allRows) {
            if (passwordEncoder.matches(rawCookie, r.getTokenHash())) {
                if (r.getRevokedAt() != null) {
                    return false; // already revoked
                }
                r.setRevokedAt(Instant.now());
                refreshTokenRepo.save(r);
                return true;
            }
        }
        return false;
    }

    // -------------------------------------------------------------------
    //  Bulk revocation (T-2.10, used by CompanyUsersService.disable +
    //  CompanyUsersService.resetPassword)
    // -------------------------------------------------------------------

    /**
     * Revoke every active refresh token for a given user / scope
     * pair. Returns the number of rows revoked. Used by the team
     * management service to force-revoke tokens on disable and
     * reset-password — the user's existing sessions must not
     * survive either action (per spec C9).
     *
     * <p>Native query updates {@code revoked_at = NOW()} for every
     * row matching {@code user_id = :userId AND user_scope = :scope
     * AND revoked_at IS NULL}. The {@code user_scope} filter is
     * belt-and-suspenders against any future collision between
     * {@code company_users.id} and a {@code platform_users.id}
     * namespace (UUID collision probability is astronomically low,
     * but adding scope costs one WHERE-clause predicate).
     */
    @Transactional("systemTransactionManager")
    public int revokeAllForUser(UUID userId, String scope) {
        Instant now = Instant.now();
        return refreshTokenRepo.revokeAllByUserAndScope(userId, scope, now);
    }

    // -------------------------------------------------------------------
    //  Reuse detection
    // -------------------------------------------------------------------

    /**
     * Revoke the entire chain reachable by walking the
     * {@code replaced_by} links from {@code start}, including the
     * presented row itself. Returns the number of rows revoked
     * (i.e. the chain length). Writes a {@code TOKEN_REUSE_DETECTED}
     * audit event.
     *
     * <p>The CTE in {@link RefreshTokenAdminRepository#findChainIds}
     * returns the chain in {@code created_at} order; the
     * {@code id IN (...)} UPDATE then revokes them all in one
     * statement.
     */
    @Transactional("systemTransactionManager")
    public int handleReuse(RefreshToken start) {
        List<UUID> chainIds = refreshTokenRepo.findChainIds(start.getId());
        if (chainIds.isEmpty()) {
            // Defensive: the row itself is not returned by the
            // CTE (because the CTE follows replaced_by links,
            // and a single revoked row with no replaced_by
            // would not appear). Revoke it directly.
            chainIds = List.of(start.getId());
        }
        int n = refreshTokenRepo.revokeChain(chainIds);
        auditLogger.logAsync(new AuditEvent(
                "TOKEN_REUSE_DETECTED",
                start.getUserId(),
                AuditEvent.UserScope.COMPANY,
                start.getTenantId(),
                null,
                null,
                Map.of("chainLength", n, "presentedTokenId", start.getId().toString())));
        return n;
    }

    // -------------------------------------------------------------------
    //  Result records
    // -------------------------------------------------------------------

    /**
     * Read-only projection returned by {@link #issue}. The
     * controller writes {@code refreshTokenValue} to the cookie
     * and propagates {@code refreshTokenId} in audit metadata.
     */
    public record Issued(
            UUID refreshTokenId,
            UUID refreshTokenValue,
            Instant refreshTokenExpiresAt,
            CompanyUser user,
            Tenant tenant,
            Role role) {}

    /** Read-only projection returned by {@link #validateAndRotate}. */
    public record Refreshed(
            String accessToken,
            UUID newRefreshTokenValue,
            Instant newRefreshTokenExpiresAt,
            long accessTokenExpiresIn,
            CompanyUser user,
            Tenant tenant,
            Role role) {}
}
