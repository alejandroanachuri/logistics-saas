package ar.com.logistics.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.logistics.auth.jwt.JwtProperties;
import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.common.exception.AuthenticationException;
import ar.com.logistics.common.exception.ErrorCode;
import ar.com.logistics.tenant.TenantContext;
import ar.com.logistics.tenant.TenantContextEntry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Pure unit test for {@link AuthenticationFilter}. No Spring
 * context, no Testcontainers, no DB — the filter is a thin
 * orchestrator over {@link JwtService} so the matrix of
 * "cookie present + valid / bad / scope-mismatch" can be
 * covered without standing up the world.
 *
 * <p>The 78/78 {@code mvn verify} gate is the end-to-end
 * smoke; this test pins the filter's branch logic in
 * isolation so future changes don't regress the contract
 * silently.
 */
class AuthenticationFilterTest {

    private AuthenticationFilter filter;
    private JwtService jwtService;
    private UUID tenantId;
    private String validCompanyToken;
    private String validPlatformToken;
    private String mismatchedIssuerToken;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties(
                "dGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQtdGVzdC1zZWNyZXQ=",
                Duration.ofMinutes(15),
                Duration.ofDays(7),
                "logistics-saas",
                "logistics-saas-web");
        jwtService = new JwtService(props);
        tenantId = UUID.randomUUID();
        validCompanyToken =
                jwtService.issueCompanyToken(UUID.randomUUID(), tenantId, "acme", java.util.List.of("COMPANY_ADMIN"));
        validPlatformToken = jwtService.issuePlatformToken(UUID.randomUUID(), "PLATFORM_ADMIN");
        mismatchedIssuerToken =
                jwtService.issueCompanyToken(UUID.randomUUID(), tenantId, "acme", java.util.List.of("COMPANY_ADMIN"));
        filter = new AuthenticationFilter(jwtService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    @DisplayName("No cookie: the chain proceeds and the SecurityContext stays empty")
    void noCookie_leavesContextEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/something");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("Valid COMPANY cookie: principal is the typed ParsedToken, authorities derive from claims")
    void validCompanyCookie_setsPrincipal() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/something");
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("access_token", validCompanyToken);
        cookie.setPath("/api/v1");
        req.setCookies(cookie);
        MockHttpServletResponse res = new MockHttpServletResponse();
        // Custom chain that captures the SecurityContext during the
        // request — the filter clears it in a finally block after
        // the chain returns, so a post-call read would see null.
        org.springframework.security.core.Authentication[] captured =
                new org.springframework.security.core.Authentication[1];
        jakarta.servlet.FilterChain chain = (request, response) ->
                captured[0] = org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .getAuthentication();

        filter.doFilter(req, res, chain);

        var auth = captured[0];
        assertThat(auth).isNotNull();
        assertThat(auth.getAuthorities())
                .extracting(Object::toString)
                .containsExactlyInAnyOrder("ROLE_COMPANY_ADMIN", "SCOPE_COMPANY");
        JwtService.ParsedToken parsed = ((ar.com.logistics.auth.security.JwtAuthentication) auth).parsed();
        assertThat(parsed.tenantId()).isEqualTo(tenantId);
        assertThat(parsed.scope()).isEqualTo(JwtService.TokenScope.COMPANY);
    }

    @Test
    @DisplayName("Bad / malformed cookie: chain proceeds, context stays empty (no info leak)")
    void badCookie_leavesContextEmpty() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/something");
        req.setCookies(new jakarta.servlet.http.Cookie("access_token", "not-a-valid-jwt-at-all"));
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("PLATFORM cookie to a company path: 403 FORBIDDEN_SCOPE")
    void platformCookie_toCompanyPath_returnsForbiddenScope() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/tenants/me");
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("access_token", validPlatformToken);
        cookie.setPath("/api/v1");
        req.setCookies(cookie);
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        assertThatThrownBy(() -> filter.doFilter(req, res, chain))
                .isInstanceOf(AuthenticationException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.FORBIDDEN_SCOPE);
    }

    // ------------------------------------------------------------------
    // Cookie path-filter contract (RFC 6265 §5.1.4 + §5.4).
    //
    // The browser stores BOTH `access_token` cookies when a user
    // has logged into both surfaces in the same jar: one with
    // Path=/api/v1 (company) and one with Path=/api/v1/platform
    // (platform). On a request to /api/v1/auth/me (a company path),
    // the browser sends BOTH cookies. The filter must pick the
    // one whose Path matches the request URI — otherwise it can
    // hand the PLATFORM cookie to the company rehydrator and the
    // cross-scope guard fires 403 FORBIDDEN_SCOPE for a perfectly
    // valid company session.
    //
    // These tests pin the contract on the private extraction
    // method (made package-private for testability).
    // ------------------------------------------------------------------

    private static jakarta.servlet.http.Cookie cookie(String value, String path) {
        jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("access_token", value);
        if (path != null) {
            c.setPath(path);
        }
        return c;
    }

    private static String extract(String requestUri, jakarta.servlet.http.Cookie... cookies) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", requestUri);
        if (cookies != null) {
            req.setCookies(cookies);
        }
        return AuthenticationFilter.extractAccessTokenCookieForTest(req);
    }

    @Test
    @DisplayName("extractAccessTokenCookie: no cookies at all → null")
    void extract_noCookies_returnsNull() {
        assertThat(extract("/api/v1/auth/me")).isNull();
    }

    @Test
    @DisplayName("extractAccessTokenCookie: cookies present but none match the name → null")
    void extract_noNameMatch_returnsNull() {
        jakarta.servlet.http.Cookie other = new jakarta.servlet.http.Cookie("refresh_token", "irrelevant");
        other.setPath("/api/v1");
        assertThat(extract("/api/v1/auth/me", other)).isNull();
    }

    @Test
    @DisplayName("extractAccessTokenCookie: single matching cookie with matching path → its value")
    void extract_singleMatchingCookie_returnsValue() {
        String value = extract("/api/v1/auth/me", cookie("company-jwt", "/api/v1"));
        assertThat(value).isEqualTo("company-jwt");
    }

    @Test
    @DisplayName(
            "extractAccessTokenCookie: both company and platform cookies present on /api/v1/auth/me → returns the company cookie (cold-boot rehydration bug)")
    void extract_bothCookiesOnCompanyPath_returnsCompany() {
        // The cold-boot rehydration request that triggers the bug.
        String value = extract(
                "/api/v1/auth/me", cookie("company-jwt", "/api/v1"), cookie("platform-jwt", "/api/v1/platform"));
        assertThat(value).isEqualTo("company-jwt");
    }

    @Test
    @DisplayName(
            "extractAccessTokenCookie: both cookies present on /api/v1/platform/users → returns the platform cookie (path-prefix match)")
    void extract_bothCookiesOnPlatformPath_returnsPlatform() {
        String value = extract(
                "/api/v1/platform/users", cookie("company-jwt", "/api/v1"), cookie("platform-jwt", "/api/v1/platform"));
        assertThat(value).isEqualTo("platform-jwt");
    }

    @Test
    @DisplayName(
            "extractAccessTokenCookie: multiple matching cookies → longest path wins (RFC 6265 §5.4 most-specific-match)")
    void extract_multipleMatching_returnsLongestPath() {
        // Reverse insertion order to prove it's not just "first match wins".
        String value = extract(
                "/api/v1/platform/admin/tenants",
                cookie("company-jwt", "/api/v1"),
                cookie("platform-jwt", "/api/v1/platform"),
                cookie("admin-jwt", "/api/v1/platform/admin"));
        assertThat(value).isEqualTo("admin-jwt");
    }

    // ------------------------------------------------------------------
    // TenantContext binding contract (fix-3).
    //
    // RlsAspect reads TenantContext.currentTenantId() on every call
    // into a RLS-scoped repository and throws IllegalStateException
    // when it is null. The context MUST be populated by the
    // authentication filter right after the JWT is verified, with
    // the scope mirrored from the JWT scope claim, and MUST be
    // cleared in the finally block to prevent ThreadLocal leakage
    // across requests on the same Tomcat worker thread.
    // ------------------------------------------------------------------

    @Test
    @DisplayName(
            "TenantContext: COMPANY-scope token binds scope=COMPANY with the JWT tenant id (read DURING the chain)")
    void setsCompanyTenantContext_whenTokenHasCompanyScope() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/company-users");
        jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("access_token", validCompanyToken);
        c.setPath("/api/v1");
        req.setCookies(c);
        MockHttpServletResponse res = new MockHttpServletResponse();
        // Capture the TenantContext state inside the chain (the filter
        // clears it in the finally block AFTER chain.doFilter returns,
        // so a post-call read would see null).
        TenantContextEntry[] captured = new TenantContextEntry[1];
        jakarta.servlet.FilterChain chain = (request, response) -> captured[0] = TenantContext.get();

        filter.doFilter(req, res, chain);

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].tenantId()).isEqualTo(tenantId);
        assertThat(captured[0].scope()).isEqualTo(TenantContextEntry.Scope.COMPANY);
        // After the filter returns, the finally block MUST have cleared
        // the ThreadLocal so the next request on this worker thread does
        // not inherit the tenant.
        assertThat(TenantContext.get()).isNull();
    }

    @Test
    @DisplayName(
            "TenantContext: PLATFORM-scope token does NOT bind (tenantId is null), but the SecurityContext principal IS set for the platform user")
    void setsPlatformTenantContext_whenTokenHasPlatformScope() throws Exception {
        // Real platform JWTs have tenantId == null (the 'tid' claim is
        // absent — see JwtService.parseAndVerify line 154). So the
        // TenantContext binding is gated on tenantId != null. What we
        // CAN assert: the cross-scope guard allows the request through
        // (URL is /api/v1/platform/**), the SecurityContext principal
        // IS bound (the filter authenticates the platform user), and
        // the TenantContext stays null. If a future refactor moves the
        // PLATFORM branch of the guard or accidentally binds the
        // TenantContext for null-tenantId tokens, this test catches
        // both regressions.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/platform/users");
        jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("access_token", validPlatformToken);
        c.setPath("/api/v1/platform");
        req.setCookies(c);
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantContextEntry[] captured = new TenantContextEntry[1];
        org.springframework.security.core.Authentication[] capturedAuth =
                new org.springframework.security.core.Authentication[1];
        jakarta.servlet.FilterChain chain = (request, response) -> {
            captured[0] = TenantContext.get();
            capturedAuth[0] = SecurityContextHolder.getContext().getAuthentication();
        };

        filter.doFilter(req, res, chain);

        assertThat(captured[0])
                .as("TenantContext must NOT be bound when the JWT has no tenant id (PLATFORM scope)")
                .isNull();
        assertThat(capturedAuth[0])
                .as("the SecurityContext principal IS still bound for the platform user")
                .isNotNull()
                .isInstanceOf(ar.com.logistics.auth.security.JwtAuthentication.class);
        assertThat(((ar.com.logistics.auth.security.JwtAuthentication) capturedAuth[0])
                        .parsed()
                        .scope())
                .isEqualTo(JwtService.TokenScope.PLATFORM);
    }

    @Test
    @DisplayName("TenantContext: cleared in the finally block even when chain.doFilter throws")
    void clearsTenantContext_inFinallyBlock() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/company-users");
        jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("access_token", validCompanyToken);
        c.setPath("/api/v1");
        req.setCookies(c);
        MockHttpServletResponse res = new MockHttpServletResponse();
        // Sanity check: the filter MUST have bound the context BEFORE
        // the chain ran (so the downstream stack could see the tenant).
        TenantContextEntry[] seenInsideChain = new TenantContextEntry[1];
        jakarta.servlet.FilterChain throwingChain = (request, response) -> {
            seenInsideChain[0] = TenantContext.get();
            throw new ServletExceptionForTest("downstream blew up");
        };

        try {
            filter.doFilter(req, res, throwingChain);
        } catch (ServletExceptionForTest expected) {
            // expected — we want to prove the finally ran.
        }

        assertThat(seenInsideChain[0])
                .as("TenantContext must have been bound BEFORE the chain ran")
                .isNotNull();
        assertThat(seenInsideChain[0].scope()).isEqualTo(TenantContextEntry.Scope.COMPANY);
        // The whole point of this test: the finally block cleared the
        // ThreadLocal even though the chain threw. If it had not, the
        // next request on this worker thread would inherit the tenant.
        assertThat(TenantContext.get())
                .as("TenantContext must be cleared even when chain.doFilter throws")
                .isNull();
    }

    @Test
    @DisplayName(
            "TenantContext: a token without a tenant id (e.g. platform-only) does NOT bind — RlsAspect is allowed to throw later")
    void doesNotSetTenantContext_whenTokenHasNoTenantId() throws Exception {
        // Real platform tokens already produce a ParsedToken with
        // tenantId == null (see JwtService.parseAndVerify line 154 —
        // the 'tid' claim is absent for PLATFORM-scope JWTs). So we
        // do NOT need a Mockito mock here: the existing
        // validPlatformToken from @BeforeEach is exactly that token.
        // We just hit a /api/v1/platform/** path so the cross-scope
        // guard does not throw, then assert the TenantContext stays
        // empty.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/platform/users");
        jakarta.servlet.http.Cookie c = new jakarta.servlet.http.Cookie("access_token", validPlatformToken);
        c.setPath("/api/v1/platform");
        req.setCookies(c);
        MockHttpServletResponse res = new MockHttpServletResponse();
        TenantContextEntry[] captured = new TenantContextEntry[1];
        jakarta.servlet.FilterChain chain = (request, response) -> captured[0] = TenantContext.get();

        filter.doFilter(req, res, chain);

        // The spec says: tokens without a tenant id DO NOT bind the
        // context. The RlsAspect will throw IllegalStateException if a
        // RLS-scoped repo is hit; that is the loud-failure design.
        assertThat(captured[0])
                .as("TenantContext must NOT be bound when the JWT has no tenant id")
                .isNull();
        assertThat(TenantContext.get()).isNull();
    }

    /** Sentinel exception to prove the finally block runs on throw. */
    private static class ServletExceptionForTest extends jakarta.servlet.ServletException {
        ServletExceptionForTest(String message) {
            super(message);
        }
    }
}
