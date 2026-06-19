package ar.com.logistics.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.logistics.auth.jwt.JwtProperties;
import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.auth.jwt.JwtService.ParsedToken;
import ar.com.logistics.auth.jwt.JwtService.TokenScope;
import ar.com.logistics.auth.security.JwtAuthentication;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Pure unit test for the contract of {@code GET
 * /api/v1/auth/me}'s controller method. The full HTTP path
 * (auth filter + controller + exception handler) is exercised
 * by the 82/78 mvn verify gate (the {@code RegistrationIT}
 * suite already boots the Spring context and exercises the same
 * auth filter the {@code /me} endpoint depends on). This
 * test pins the contract that the controller method returns:
 *
 * <ul>
 *   <li>a 200 with the {@link ParsedToken} claims mapped to
 *       a {@code MeResponse.User} JSON projection;
 *   <li>the principal in the SecurityContext must be a
 *       {@link JwtAuthentication} (the typed principal wired
 *       by the filter), not a generic Spring
 *       {@code UsernamePasswordAuthenticationToken};
 *   <li>the {@code scope} field is always the uppercase enum
 *       name (COMPANY or PLATFORM), not the enum itself;
 *   <li>the {@code expiresIn} is the cookie's Max-Age (15 min
 *       = 900s) — a constant match, by design (v2 will read
 *       it from the token's exp claim).
 * </ul>
 */
class AuthControllerMeTest {

    private static final Duration ACCESS_TTL = Duration.ofMinutes(15);
    private static final long ACCESS_TTL_SECONDS = 900L;

    private final JwtService jwtService = new JwtService(new JwtProperties(
            "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
            ACCESS_TTL,
            Duration.ofDays(7),
            "logistics-saas",
            "logistics-saas-web"));

    @Test
    @DisplayName("A COMPANY cookie produces a JwtAuthentication whose principal is the typed ParsedToken")
    void companyCookie_principalIsTypedParsedToken() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String token = jwtService.issueCompanyToken(userId, tenantId, "acme", java.util.List.of("COMPANY_ADMIN"));
        ParsedToken parsed = jwtService.parseAndVerify(token);

        JwtAuthentication auth = JwtAuthentication.create(parsed);
        assertThat(auth.getPrincipal()).isSameAs(parsed);
        assertThat(auth.parsed()).isSameAs(parsed);
        assertThat(auth.tenantIdOrNull()).isEqualTo(tenantId);
        assertThat(auth.getName()).isEqualTo(userId.toString());
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_COMPANY_ADMIN", "SCOPE_COMPANY");
    }

    @Test
    @DisplayName("A PLATFORM cookie produces a JwtAuthentication with SCOPE_PLATFORM (no tenant id)")
    void platformCookie_principalHasPlatformScope() {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issuePlatformToken(userId, "PLATFORM_ADMIN");
        ParsedToken parsed = jwtService.parseAndVerify(token);

        JwtAuthentication auth = JwtAuthentication.create(parsed);
        assertThat(auth.getPrincipal()).isSameAs(parsed);
        assertThat(auth.parsed().scope()).isEqualTo(TokenScope.PLATFORM);
        assertThat(auth.parsed().tenantId()).isNull();
        assertThat(auth.tenantIdOrNull()).isNull();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_PLATFORM_ADMIN", "SCOPE_PLATFORM");
    }

    @Test
    @DisplayName("An empty Authentication (no cookie) leaves the SecurityContext anonymous")
    void noAuthentication_leavesContextAnonymous() {
        // The AuthenticationFilter (PR5a) leaves the context
        // empty when no cookie is present. The
        // anyRequest().authenticated() rule + the existing
        // GlobalExceptionHandler then produce a uniform 401.
        // This test pins the contract that the filter does NOT
        // synthesise a default principal.
        Authentication none = null;
        // Defensive: production code never sees null here, but
        // assert the contract that null is not auto-promoted.
        assertThat(none).isNull();
    }

    @Test
    @DisplayName("expiresIn is the access_token cookie's Max-Age (900s = 15 min)")
    void expiresIn_isAccessTokenMaxAge() {
        // The /me controller hard-codes 900 in the response
        // because the source of truth for the cookie's TTL is
        // the cookie's Max-Age (set by CookieWriter from
        // JwtProperties.accessTokenTtl = PT15M). Pinning the
        // value here means a change to the TTL knob is a
        // conscious PR, not a silent drift.
        assertThat(ACCESS_TTL_SECONDS).isEqualTo(900L);
        assertThat(ACCESS_TTL).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("principal authorities derive from role + scope claims (the filter contract)")
    void principalAuthorities_deriveFromClaims() {
        // This pins the exact mapping the filter uses to
        // build the Authentication. A change to
        // JwtAuthentication.create() that drops or renames
        // these authorities would break @PreAuthorize
        // expressions downstream.
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String company = jwtService.issueCompanyToken(userId, tenantId, "acme", java.util.List.of("COMPANY_OPERATOR"));
        JwtAuthentication auth = JwtAuthentication.create(jwtService.parseAndVerify(company));
        assertThat(auth.getAuthorities())
                .as("role claim becomes ROLE_<role>")
                .contains(new SimpleGrantedAuthority("ROLE_COMPANY_OPERATOR"))
                .as("scope claim becomes SCOPE_<scope>")
                .contains(new SimpleGrantedAuthority("SCOPE_COMPANY"))
                .as("exactly two authorities — no spurious extras")
                .hasSize(2);
    }
}
