package ar.com.logistics.auth.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ar.com.logistics.auth.jwt.JwtProperties;
import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.common.exception.AuthenticationException;
import ar.com.logistics.common.exception.ErrorCode;
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
        jakarta.servlet.http.Cookie other =
                new jakarta.servlet.http.Cookie("refresh_token", "irrelevant");
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
    @DisplayName("extractAccessTokenCookie: both company and platform cookies present on /api/v1/auth/me → returns the company cookie (cold-boot rehydration bug)")
    void extract_bothCookiesOnCompanyPath_returnsCompany() {
        // The cold-boot rehydration request that triggers the bug.
        String value = extract(
                "/api/v1/auth/me",
                cookie("company-jwt", "/api/v1"),
                cookie("platform-jwt", "/api/v1/platform"));
        assertThat(value).isEqualTo("company-jwt");
    }

    @Test
    @DisplayName("extractAccessTokenCookie: both cookies present on /api/v1/platform/users → returns the platform cookie (path-prefix match)")
    void extract_bothCookiesOnPlatformPath_returnsPlatform() {
        String value = extract(
                "/api/v1/platform/users",
                cookie("company-jwt", "/api/v1"),
                cookie("platform-jwt", "/api/v1/platform"));
        assertThat(value).isEqualTo("platform-jwt");
    }

    @Test
    @DisplayName("extractAccessTokenCookie: multiple matching cookies → longest path wins (RFC 6265 §5.4 most-specific-match)")
    void extract_multipleMatching_returnsLongestPath() {
        // Reverse insertion order to prove it's not just "first match wins".
        String value = extract(
                "/api/v1/platform/admin/tenants",
                cookie("company-jwt", "/api/v1"),
                cookie("platform-jwt", "/api/v1/platform"),
                cookie("admin-jwt", "/api/v1/platform/admin"));
        assertThat(value).isEqualTo("admin-jwt");
    }
}
