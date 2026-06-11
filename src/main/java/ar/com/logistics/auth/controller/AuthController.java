package ar.com.logistics.auth.controller;

import ar.com.logistics.auth.dto.RegisterRequest;
import ar.com.logistics.auth.dto.RegisterResponse;
import ar.com.logistics.auth.service.RegistrationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth endpoints under {@code /api/v1/auth}. In PR3 this only
 * exposes the self-service registration endpoint. Login, refresh,
 * logout, and {@code /auth/me} land in PR4 (along with the cookie
 * issuer and the JWT authentication filter).
 *
 * <p>All routes here are declared {@code permitAll()} in
 * {@link ar.com.logistics.config.SecurityConfig} (registration is
 * anonymous).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegistrationService registrationService;

    public AuthController(RegistrationService registrationService) {
        this.registrationService = registrationService;
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
}
