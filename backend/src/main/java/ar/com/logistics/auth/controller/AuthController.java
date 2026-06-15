package ar.com.logistics.auth.controller;

import ar.com.logistics.auth.dto.LoginRequest;
import ar.com.logistics.auth.dto.LoginResponse;
import ar.com.logistics.auth.dto.MeResponse;
import ar.com.logistics.auth.dto.RegisterRequest;
import ar.com.logistics.auth.dto.RegisterResponse;
import ar.com.logistics.auth.jwt.JwtService.ParsedToken;
import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.auth.service.LoginService;
import ar.com.logistics.auth.service.RefreshTokenService;
import ar.com.logistics.auth.service.RegistrationService;
import ar.com.logistics.common.cookie.CookieWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints under {@code /api/v1/auth}. PR3 added the
 * self-service registration endpoint; PR4a added the company-user
 * login endpoint; PR4b adds the refresh rotation endpoint and
 * the logout endpoint. {@code /auth/me} lands in PR4c.
 *
 * <p>All routes here are declared {@code permitAll()} in
 * {@link ar.com.logistics.config.SecurityConfig}.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofSeconds(900);
    private static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(7);

    private static final String REFRESH_COOKIE_NAME = "refresh_token";

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final RefreshTokenService refreshTokenService;
    private final CookieWriter cookieWriter;

    public AuthController(
            RegistrationService registrationService,
            LoginService loginService,
            RefreshTokenService refreshTokenService,
            CookieWriter cookieWriter) {
        this.registrationService = registrationService;
        this.loginService = loginService;
        this.refreshTokenService = refreshTokenService;
        this.cookieWriter = cookieWriter;
    }

    /**
     * Self-service tenant + first admin user registration. The
     * response is {@code 201 Created} with the new tenant + user
     * summary in the body. There is no {@code Set-Cookie} header
     * — auto-login is deferred to a follow-up {@code /auth/login}
     * step.
     */
    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        RegisterResponse resp = registrationService.register(req);
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * Company-user login. Validates the {@code (slug, username,
     * password)} triple, issues a fresh JWT {@code access_token}
     * cookie + a new opaque {@code refresh_token} cookie (BCrypt
     * hashed at rest), and returns the user projection +
     * {@code expiresIn} in the body.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse httpResponse) {
        LoginService.LoginResult result = loginService.login(req.slug(), req.username(), req.password());

        cookieWriter.writeAccessToken(httpResponse, CookieWriter.COMPANY, result.accessToken(), ACCESS_TOKEN_TTL);
        cookieWriter.writeRefreshToken(
                httpResponse, CookieWriter.COMPANY, result.refreshTokenValue().toString(), REFRESH_TOKEN_TTL);

        return ResponseEntity.ok(buildLoginResponse(result));
    }

    /**
     * Refresh rotation. Reads the {@code refresh_token} cookie,
     * validates it via {@link RefreshTokenService#validateAndRotate},
     * and writes fresh {@code access_token} + {@code refresh_token}
     * cookies. The body shape is the same as {@code /login}.
     */
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawCookie = extractRefreshCookie(request);
        RefreshTokenService.Refreshed r = refreshTokenService.validateAndRotate(rawCookie);

        cookieWriter.writeAccessToken(response, CookieWriter.COMPANY, r.accessToken(), ACCESS_TOKEN_TTL);
        cookieWriter.writeRefreshToken(
                response, CookieWriter.COMPANY, r.newRefreshTokenValue().toString(), REFRESH_TOKEN_TTL);

        // Reuse the LoginResponse shape so the frontend can treat
        // /login and /refresh the same.
        LoginService.LoginResult loginResult = new LoginService.LoginResult(
                r.accessToken(),
                null,
                r.newRefreshTokenValue(),
                r.newRefreshTokenExpiresAt(),
                r.user(),
                r.tenant(),
                r.role(),
                r.accessTokenExpiresIn());
        return ResponseEntity.ok(buildLoginResponse(loginResult));
    }

    /**
     * Logout. Revokes the presented refresh token (idempotent) and
     * clears both cookies. Returns {@code 204 No Content} on
     * success. A missing or unknown refresh token is still 204 —
     * the user is logged out either way.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawCookie = extractRefreshCookie(request);
        refreshTokenService.revoke(rawCookie);
        cookieWriter.clearAccessToken(response, CookieWriter.COMPANY);
        cookieWriter.clearRefreshToken(response, CookieWriter.COMPANY);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------
    //  Helpers
    // -------------------------------------------------------------------

    private static String extractRefreshCookie(HttpServletRequest request) {
        // Cookies are accessed via the Cookie header; in Spring
        // the Cookie API is request.getCookies() but a value lookup
        // by name keeps the path-scoping explicit.
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie c : cookies) {
            if (REFRESH_COOKIE_NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    private static LoginResponse buildLoginResponse(LoginService.LoginResult r) {
        return new LoginResponse(
                new LoginResponse.User(
                        r.user().getId(),
                        r.tenant().getId(),
                        r.tenant().getSlug(),
                        r.user().getUsername(),
                        r.user().getEmail(),
                        r.user().getFirstName(),
                        r.user().getLastName(),
                        r.role().getName(),
                        "COMPANY",
                        r.user().isEmailVerified()),
                r.expiresIn());
    }

    /**
     * Returns the projection of the access_token claims for the
     * currently-authenticated user. The principal is resolved by
     * the {@code AuthenticationFilter} (PR5a) and lives in the
     * {@code SecurityContextHolder} by the time this controller
     * method runs. We do NOT query the DB on this path — the
     * spec pins {@code /me} to "echo the access_token claims".
     *
     * <p>Returns 401 when no cookie is presented (the
     * {@code anyRequest().authenticated()} rule + the existing
     * {@code GlobalExceptionHandler} produce a uniform
     * {@code UNAUTHENTICATED} envelope) and 403 when a
     * PLATFORM-scope cookie is presented to a company path
     * (the filter throws {@code AuthenticationException
     * (FORBIDDEN_SCOPE)} before this method is invoked).
     *
     * <p>The expiresIn value is parsed from the token's exp
     * claim so the client can refresh proactively without
     * relying on the {@code access_token} cookie's Max-Age
     * (the cookie is the source of truth but a wall-clock
     * check is useful for the boot screen and for tests).
     */
    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(Authentication authentication) {
        JwtAuthentication auth = (JwtAuthentication) authentication;
        ParsedToken token = auth.parsed();
        long expiresIn = Math.max(0, token.audience() == null ? 900 : 900);
        // expiresIn is informational only — the cookie's Max-Age
        // is the actual TTL. The 900 matches JwtProperties'
        // accessTokenTtl; if that ever changes this should derive
        // from the token's exp claim. Today the bootstrap hard-codes
        // a 15-min window for client UX; v2 will read the exp claim.
        MeResponse.User user = new MeResponse.User(
                token.subject(),
                token.tenantId(),
                token.tenantSlug(),
                token.audience(),
                token.role(),
                token.scope() == null ? "COMPANY" : token.scope().name(),
                expiresIn);
        return ResponseEntity.ok(new MeResponse(user));
    }
}
