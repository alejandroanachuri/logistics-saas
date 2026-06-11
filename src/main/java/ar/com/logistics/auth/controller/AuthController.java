package ar.com.logistics.auth.controller;

import ar.com.logistics.auth.dto.LoginRequest;
import ar.com.logistics.auth.dto.LoginResponse;
import ar.com.logistics.auth.dto.RegisterRequest;
import ar.com.logistics.auth.dto.RegisterResponse;
import ar.com.logistics.auth.service.LoginService;
import ar.com.logistics.auth.service.RegistrationService;
import ar.com.logistics.common.cookie.CookieWriter;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints under {@code /api/v1/auth}. PR3 added the
 * self-service registration endpoint; PR4a adds the company-user
 * login endpoint. Refresh, logout, and {@code /auth/me} land in
 * PR4b/c.
 *
 * <p>All routes here are declared {@code permitAll()} in
 * {@link ar.com.logistics.config.SecurityConfig} (registration and
 * login are anonymous).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private static final Duration ACCESS_TOKEN_TTL = Duration.ofSeconds(900);

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final CookieWriter cookieWriter;

    public AuthController(
            RegistrationService registrationService, LoginService loginService, CookieWriter cookieWriter) {
        this.registrationService = registrationService;
        this.loginService = loginService;
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
     * cookie, and returns the user projection + {@code expiresIn}
     * in the body.
     *
     * <p>PR4a only writes the access_token cookie. The refresh
     * cookie lands in PR4b along with the {@code refresh_tokens}
     * row insertion.
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse httpResponse) {
        LoginService.LoginResult result = loginService.login(req.slug(), req.username(), req.password());

        // Write the access_token cookie. SameSite=Strict, HttpOnly,
        // Path=/api/v1, Max-Age=900 (15 min, per spec).
        cookieWriter.writeAccessToken(httpResponse, CookieWriter.COMPANY, result.accessToken(), ACCESS_TOKEN_TTL);

        LoginResponse body = new LoginResponse(
                new LoginResponse.User(
                        result.user().getId(),
                        result.tenant().getId(),
                        result.tenant().getSlug(),
                        result.user().getUsername(),
                        result.user().getEmail(),
                        result.user().getFirstName(),
                        result.user().getLastName(),
                        result.role().getName(),
                        "COMPANY",
                        result.user().isEmailVerified()),
                result.expiresIn());
        return ResponseEntity.ok(body);
    }
}
