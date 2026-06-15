package ar.com.logistics.auth.repository.system;

import ar.com.logistics.auth.domain.RefreshToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Refresh-token lookup / persistence bound to the system-side
 * DataSource (BYPASSRLS). The {@code refresh_tokens} table has
 * no RLS — every row is addressable by anyone with the unique
 * {@code token_hash} — so a single repo covers both COMPANY and
 * PLATFORM scopes. The cross-tenant lookups in this table are
 * safe because the {@code token_hash} is a server-generated
 * BCrypt value the client never sees in raw form past issuance.
 *
 * <p>This is the {@code system}-side counterpart to a
 * not-yet-written {@code company}-side repo. The
 * {@code company}-side repo (when added) would be RLS-scoped
 * and only useful for tenant-scoped queries; the spec
 * intentionally uses system-side here because the lookup is
 * keyed by the {@code token_hash} (not by tenant) and the
 * access pattern is one-lookup-per-request, not a list.
 */
public interface RefreshTokenAdminRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Find a row by its {@code token_hash} (the BCrypt of the
     * raw UUID the client stores in the cookie). Used by
     * {@code /refresh} and {@code /logout} to resolve the
     * presented cookie to a row before rotating or revoking.
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Walk the {@code replaced_by} chain starting at the row with
     * id {@code startId} (the row the attacker just presented).
     * Returns every row reachable by following
     * {@code replaced_by → id} until a row whose
     * {@code replaced_by IS NULL} is reached (the head of the
     * chain). Used by the reuse-detection branch in
     * {@code RefreshTokenService}.
     *
     * <p>Implementation: a single recursive CTE in Postgres.
     * The result is ordered by {@code created_at} ascending so
     * the caller can iterate from the presented row up to the
     * head.
     */
    @Query(
            value =
                    """
                    WITH RECURSIVE chain AS (
                        SELECT id, user_id, user_scope, tenant_id, token_hash,
                               expires_at, revoked_at, replaced_by, created_at
                          FROM public.refresh_tokens
                         WHERE id = :startId
                        UNION ALL
                        SELECT t.id, t.user_id, t.user_scope, t.tenant_id, t.token_hash,
                               t.expires_at, t.revoked_at, t.replaced_by, t.created_at
                          FROM public.refresh_tokens t
                          JOIN chain c ON t.id = c.replaced_by
                    )
                    SELECT id FROM chain
                    """,
            nativeQuery = true)
    List<UUID> findChainIds(@Param("startId") UUID startId);

    /**
     * Revoke every row in {@code ids} whose {@code revoked_at}
     * is still NULL. Returns the number of rows touched. Used by
     * the reuse-detection branch in
     * {@code RefreshTokenService.handleReuse} as the single
     * atomic UPDATE that closes the whole chain.
     */
    @Modifying
    @Query(
            value =
                    """
                    UPDATE public.refresh_tokens
                       SET revoked_at = NOW()
                     WHERE id = ANY(CAST(:ids AS uuid[]))
                       AND revoked_at IS NULL
                    """,
            nativeQuery = true)
    int revokeChain(@Param("ids") List<UUID> ids);

    /**
     * Insert a row bypassing JPA's setter API (the entity is
     * {@code @Getter}-only by design — see
     * {@code RefreshTokenService.persistRefreshRow} for the
     * rationale). Native insert keeps the entity pure: no
     * setters, no JPA-managed field assignments.
     *
     * <p>The {@code scope} parameter is typed {@code String} (not
     * {@code UserScope}) because native queries do not honor
     * JPA's {@code @Enumerated(EnumType.STRING)} mapping. The
     * caller is responsible for passing {@code UserScope.name()}
     * (e.g. {@code "COMPANY"}, not the enum constant). Hibernate
     * 7 with a native query + a Java enum param sends the
     * ordinal (0, 1) by default, which fails the
     * {@code refresh_tokens_user_scope_check} CHECK constraint
     * that requires the string values {@code 'COMPANY'} or
     * {@code 'PLATFORM'}. Discovered via F1 end-to-end testing on
     * 2026-06-15: the first login after register failed at the
     * refresh-token insert with
     * {@code DataIntegrityViolationException: ... violates check
     * constraint "refresh_tokens_user_scope_check"} because the
     * param binding sent 0 instead of 'COMPANY'.
     */
    @Modifying
    @Query(
            value =
                    """
                    INSERT INTO public.refresh_tokens
                        (id, user_id, user_scope, tenant_id, token_hash, expires_at)
                    VALUES
                        (:id, :userId, :scope, :tenantId, :tokenHash, :expiresAt)
                    """,
            nativeQuery = true)
    void insertRow(
            @Param("id") UUID id,
            @Param("userId") UUID userId,
            @Param("scope") String scope,
            @Param("tenantId") UUID tenantId,
            @Param("tokenHash") String tokenHash,
            @Param("expiresAt") Instant expiresAt);
}
