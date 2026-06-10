package ar.com.logistics.auth.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the access tokens used by the company and platform
 * paths. Refresh tokens are opaque UUIDs persisted in
 * {@code refresh_tokens} and are NOT issued by this service (see
 * {@code design.md} §4 and ADR-0004).
 *
 * <p>Shape per ADR-0003:
 * <ul>
 * <li>Company access token claims:
 * {@code sub} (company_user_id), {@code tid} (tenant_id),
 * {@code slug} (tenant_slug), {@code role}, {@code scope=COMPANY},
 * {@code iat}, {@code exp}, {@code iss}, {@code aud}.</li>
 * <li>Platform access token claims:
 * {@code sub} (platform_user_id), {@code role},
 * {@code scope=PLATFORM}, {@code iat}, {@code exp}, {@code iss},
 * {@code aud}. No {@code tid} / {@code slug} (platform users are
 * cross-tenant).</li>
 * </ul>
 *
 * <p>Algorithm: HS256. The secret is loaded from
 * {@code app.jwt.secret} as a base64-encoded byte array of at least
 *256 bits (32 bytes). At startup a shorter key triggers
 * {@link IllegalStateException} so the operator notices immediately.
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final Duration accessTtl;
    private final String issuer;
    private final String audience;

    public JwtService(JwtProperties props) {
        byte[] keyBytes = props.secret();
        if (keyBytes == null || keyBytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be base64-encoded and at least256 bits (32 bytes) long. Got "
                            + (keyBytes == null ? 0 : keyBytes.length) + " bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTtl = props.accessTokenTtl();
        this.issuer = props.issuer();
        this.audience = props.audience();
    }

    /** Issues a company-scope access token. */
    public String issueCompanyToken(UUID userId, UUID tenantId, String tenantSlug, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("slug", tenantSlug)
                .claim("role", role)
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
     * Parses and verifies the token, returning the typed claims.
     * Throws {@link io.jsonwebtoken.JwtException} on any problem
     * (bad signature, expired, malformed). Callers map to the
     * appropriate {@code ErrorCode}.
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
        return new ParsedToken(
                UUID.fromString(c.getSubject()),
                tid,
                c.get("slug", String.class),
                c.get("role", String.class),
                scope,
                c.getIssuer(),
                c.getAudience() == null
                        ? null
                        : c.getAudience().stream().findFirst().orElse(null));
    }

    public enum TokenScope {
        COMPANY,
        PLATFORM
    }

    /**
     * Typed projection of the relevant claims. The raw {@code Claims}
     * object is intentionally not exposed — callers see a stable
     * record shape and cannot accidentally leak a verification
     * artifact.
     */
    public record ParsedToken(
            UUID subject,
            UUID tenantId,
            String tenantSlug,
            String role,
            TokenScope scope,
            String issuer,
            String audience) {}
}
