package ar.com.logistics.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the access tokens used by the company and platform
 * paths. Refresh tokens are opaque UUIDs persisted in
 * {@code refresh_tokens} and are NOT issued by this service (see
 * {@code design.md} section4 and ADR-0004).
 *
 * <p>Shape per ADR-0003:
 * <ul>
 * <li>Company access token claims: sub (company_user_id), tid (tenant_id),
 * slug (tenant_slug), role, scope=COMPANY, iat, exp, iss, aud.</li>
 * <li>Platform access token claims: sub (platform_user_id), role,
 * scope=PLATFORM, iat, exp, iss, aud. No tid / slug (platform users
 * are cross-tenant).</li>
 * </ul>
 *
 * <p>Algorithm: HS256. The secret is loaded from app.jwt.secret as a
 * base64-encoded string; we decode it to bytes and require at least
 * 256 bits (32 bytes). At startup a shorter key triggers
 * {@link IllegalStateException} so the operator notices immediately.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final String issuer;
    private final String audience;

    public JwtService(JwtProperties props) {
        byte[] keyBytes = decodeSecret(props.secret());
        if (keyBytes == null || keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be base64 encoded and at least 256 bits (32 bytes) long. Got "
                            + (keyBytes == null ? 0 : keyBytes.length) + " bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = props.accessTokenTtl();
        this.issuer = props.issuer();
        this.audience = props.audience();
    }

    /**
     * Decode the base64 encoded HMAC secret. Returns null if the raw
     * value is missing or empty; the caller validates length.
     */
    private static byte[] decodeSecret(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Base64.getDecoder().decode(raw);
        } catch (IllegalArgumentException ex) {
            // Surface a clearer message at startup; the IllegalStateException
            // thrown by the constructor is more diagnostic than Spring's
            // generic bind error.
            throw new IllegalStateException(
                    "app.jwt.secret is not valid base64. Check the value in application.yml or the JWT_SECRET env var.",
                    ex);
        }
    }

    /**
     * Issues a company-scope access token.
     *
     * <p>The {@code roles} parameter is the multi-role list assigned
     * to the user (etapa-2 onward). The JWT carries:
     * <ul>
     *   <li>{@code roles} — a JSON array of role names (canonical
     *       contract from PR-3 onward).</li>
     *   <li>{@code role} — the first/primary role as a string, kept
     *       for backwards-compat with single-role clients that have
     *       not yet migrated to the new claim shape.</li>
     * </ul>
     * When {@code roles} is null or empty the call falls back to an
     * empty list (the {@code role} claim stays null — the legacy
     * clients treat null as "no role" and the new clients treat an
     * empty list the same way).
     */
    public String issueCompanyToken(UUID userId, UUID tenantId, String tenantSlug, List<String> roles) {
        Instant now = Instant.now();
        List<String> normalized = roles == null ? List.of() : List.copyOf(roles);
        String primaryRole = normalized.isEmpty() ? null : normalized.get(0);
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("slug", tenantSlug)
                .claim("role", primaryRole)
                .claim("roles", normalized)
                .claim("scope", "COMPANY")
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey)
                .compact();
    }

    /** Issues a platform-scope access token. {@code tenantId} must be {@code null}. */
    public String issuePlatformToken(UUID userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("role", role)
                .claim("scope", "PLATFORM")
                .issuer(issuer)
                .audience()
                .add(audience)
                .and()
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtl)))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies the token, returning the typed claims. Throws
     * {@link io.jsonwebtoken.JwtException} on any problem (bad signature,
     * expired, malformed). Callers map to the appropriate {@code ErrorCode}.
     *
     * <p>Multi-role handling (etapa-2 PR-3 onward): the JWT carries
     * both {@code role} (legacy single string, kept for backwards-compat)
     * AND {@code roles} (the canonical list). When the token is a fresh
     * PR-3 token both fields are populated; when it's an old (pre-PR-3)
     * token the {@code roles} claim is missing and the parser wraps the
     * single {@code role} value in a singleton list so callers always
     * see a {@code List<String>}.
     */
    public ParsedToken parseAndVerify(String token) {
        Claims c = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String scopeStr = c.get("scope", String.class);
        TokenScope scope = scopeStr == null ? null : TokenScope.valueOf(scopeStr);
        UUID tid = c.get("tid") == null ? null : UUID.fromString(c.get("tid", String.class));
        String legacyRole = c.get("role", String.class);
        List<String> roles = extractRoles(c, legacyRole);
        return new ParsedToken(
                UUID.fromString(c.getSubject()),
                tid,
                c.get("slug", String.class),
                legacyRole,
                roles,
                scope,
                c.getIssuer(),
                c.getAudience() == null
                        ? null
                        : c.getAudience().stream().findFirst().orElse(null));
    }

    /**
     * Resolve the {@code roles[]} claim to a {@link List<String>}.
     * Falls back to a singleton list wrapping the legacy {@code role}
     * claim when the token is pre-PR-3 (no {@code roles} claim).
     * Returns an empty list when neither claim is set.
     */
    @SuppressWarnings("unchecked")
    private static List<String> extractRoles(Claims c, String legacyRole) {
        Object raw = c.get("roles");
        if (raw instanceof List<?> list) {
            // Each element is the role name (Jackson deserialises
            // JSON string arrays to List<String>). Defensive cast.
            List<String> out = new ArrayList<>(list.size());
            for (Object e : list) {
                if (e != null) {
                    out.add(e.toString());
                }
            }
            return Collections.unmodifiableList(out);
        }
        // No roles[] claim — wrap the legacy role in a singleton
        // list (or empty if also missing) so the contract is stable.
        if (legacyRole != null && !legacyRole.isBlank()) {
            return List.of(legacyRole);
        }
        return List.of();
    }

    public enum TokenScope {
        COMPANY,
        PLATFORM
    }

    /**
     * Typed projection of the relevant claims. The raw {@code Claims}
     * object is intentionally not exposed; callers see a stable record
     * shape and cannot accidentally leak a verification artifact.
     *
     * <p>Multi-role projection (etapa-2 PR-3 onward): both the
     * legacy single {@code role} field and the canonical {@code roles}
     * list are exposed. {@code role} is always the first element of
     * {@code roles} (or null when the list is empty).
     */
    public record ParsedToken(
            UUID subject,
            UUID tenantId,
            String tenantSlug,
            String role,
            List<String> roles,
            TokenScope scope,
            String issuer,
            String audience) {}
}
