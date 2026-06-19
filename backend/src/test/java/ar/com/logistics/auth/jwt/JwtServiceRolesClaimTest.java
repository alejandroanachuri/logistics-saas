package ar.com.logistics.auth.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Breaking-change regression tests for the etapa-2 PR-3 transition
 * from a single {@code role: String} JWT claim to a {@code roles: List<String>}
 * claim. The original {@code role} claim is preserved as the first/primary
 * role for backwards-compat with single-role clients (the F1 frontend
 * still reads it until PR-4 deploys).
 *
 * <p>Strict TDD — every test below pins a piece of the JWT shape
 * contract from spec §D before the implementation exists.
 */
class JwtServiceRolesClaimTest {

    private static final String SECRET_B64 =
            Base64.getEncoder().encodeToString("test-secret-test-secret-test-secret-test-secret-test".getBytes());

    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                SECRET_B64, Duration.ofMinutes(15), Duration.ofDays(7), "logistics-saas", "logistics-saas-web");
        jwtService = new JwtService(props);
        // Same key the service uses, so we can decode + re-parse the
        // token below to assert on the claim shape.
        signingKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET_B64));
    }

    @Test
    @DisplayName("issueCompanyToken with a single role emits both 'role' and 'roles[]' claims")
    void singleRole_emitsBothClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(userId, tenantId, "acme", List.of("COMPANY_ADMIN"));

        Claims claims = decode(token);

        // The new contract: roles[] is the canonical list.
        @SuppressWarnings("unchecked")
        List<Object> rolesClaim = claims.get("roles", List.class);
        assertThat(rolesClaim).containsExactly("COMPANY_ADMIN");

        // Backwards-compat: role claim still carries the first/primary
        // role for clients that have not migrated yet.
        assertThat(claims.get("role", String.class)).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    @DisplayName("issueCompanyToken with multiple roles emits roles[] in the order supplied")
    void multipleRoles_emitsRolesArrayInOrder() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(
                userId, tenantId, "acme", List.of("COMPANY_ADMIN", "COMPANY_OPERATOR", "COMPANY_DRIVER"));

        Claims claims = decode(token);

        @SuppressWarnings("unchecked")
        List<Object> rolesClaim = claims.get("roles", List.class);
        assertThat(rolesClaim).containsExactly("COMPANY_ADMIN", "COMPANY_OPERATOR", "COMPANY_DRIVER");
        // The 'role' claim falls back to the first/primary role.
        assertThat(claims.get("role", String.class)).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    @DisplayName("parseAndVerify exposes the roles[] claim as a List<String>")
    void parseAndVerify_exposesRolesArray() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token =
                jwtService.issueCompanyToken(userId, tenantId, "acme", List.of("COMPANY_VIEWER", "COMPANY_OPERATOR"));

        JwtService.ParsedToken parsed = jwtService.parseAndVerify(token);

        assertThat(parsed.roles()).containsExactly("COMPANY_VIEWER", "COMPANY_OPERATOR");
        // The legacy single-role field is preserved as the first role
        // for clients that have not yet migrated to the new claim.
        assertThat(parsed.role()).isEqualTo("COMPANY_VIEWER");
    }

    @Test
    @DisplayName("parseAndVerify with a single-role token populates both fields consistently")
    void singleRole_parseAndVerifyConsistentFields() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(userId, tenantId, "acme", List.of("COMPANY_ADMIN"));

        JwtService.ParsedToken parsed = jwtService.parseAndVerify(token);

        assertThat(parsed.roles()).containsExactly("COMPANY_ADMIN");
        assertThat(parsed.role()).isEqualTo("COMPANY_ADMIN");
    }

    @Test
    @DisplayName("parseAndVerify with a roles[] token keeps scope=COMPANY + tid + slug intact")
    void parseAndVerify_preservesOtherClaims() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(userId, tenantId, "acme", List.of("COMPANY_OPERATOR"));

        JwtService.ParsedToken parsed = jwtService.parseAndVerify(token);

        assertThat(parsed.subject()).isEqualTo(userId);
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.tenantSlug()).isEqualTo("acme");
        assertThat(parsed.scope()).isEqualTo(JwtService.TokenScope.COMPANY);
        assertThat(parsed.issuer()).isEqualTo("logistics-saas");
    }

    @Test
    @DisplayName(
            "A token issued under the OLD signature (single role string) still parses — the role is wrapped in a singleton list")
    void legacySingleRoleToken_parsesAsSingleton() {
        // Simulate an old token: only the 'role' claim is set, no
        // 'roles[]'. The parser must wrap the single role in a
        // singleton list so callers reading parsed.roles() always see
        // a List<String>, never null.
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String legacyToken = Jwts.builder()
                .subject(userId.toString())
                .claim("tid", tenantId.toString())
                .claim("slug", "acme")
                .claim("role", "COMPANY_ADMIN")
                .claim("scope", "COMPANY")
                .issuer("logistics-saas")
                .audience()
                .add("logistics-saas-web")
                .and()
                .issuedAt(new java.util.Date())
                .expiration(new java.util.Date(System.currentTimeMillis() + 900_000))
                .signWith(signingKey)
                .compact();

        JwtService.ParsedToken parsed = jwtService.parseAndVerify(legacyToken);

        // The legacy role is exposed as both fields, wrapped in a list.
        assertThat(parsed.role()).isEqualTo("COMPANY_ADMIN");
        assertThat(parsed.roles()).containsExactly("COMPANY_ADMIN");
    }

    @Test
    @DisplayName("parseAndVerify with a roles[] token — list order is preserved end-to-end")
    void rolesArrayOrder_preservedThroughParse() {
        // Pinning order matters because the frontend's "primary role"
        // detection (currentUserIsAdmin) reads .roles().contains(...)
        // — order-independent — but backend code may rely on
        // .roles().get(0) for the "primary" role.
        List<String> sourceList = List.of("COMPANY_ADMIN", "COMPANY_OPERATOR", "COMPANY_DRIVER", "COMPANY_VIEWER");
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(userId, tenantId, "acme", sourceList);

        JwtService.ParsedToken parsed = jwtService.parseAndVerify(token);

        assertThat(parsed.roles()).containsExactlyElementsOf(sourceList);
    }

    private Claims decode(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
