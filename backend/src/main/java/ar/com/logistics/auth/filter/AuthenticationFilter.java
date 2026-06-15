package ar.com.logistics.auth.filter;

import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.auth.jwt.JwtService.ParsedToken;
import ar.com.logistics.auth.jwt.JwtService.TokenScope;
import ar.com.logistics.auth.security.JwtAuthentication;
import ar.com.logistics.common.exception.AuthenticationException;
import ar.com.logistics.common.exception.ErrorCode;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Translates the {@code access_token} cookie into a Spring
 * {@code Authentication} so any endpoint marked
 * {@code authenticated()} in {@code SecurityConfig} actually runs
 * as that user.
 *
 * <p>Path scoping per ADR-0005: the cookie name
 * {@code access_token} is read from the request's
 * {@code Cookie} header. The cookie's {@code Path} attribute is
 * not consulted here (browsers do send a cookie to any URL under
 * the path it was set with; the path is the server-side contract
 * for the {@code CookieWriter}). A PLATFORM cookie presented to a
 * company path returns 403 {@code FORBIDDEN_SCOPE} per spec
 * (refresh-token-rotation.md cross-cutting requirement).
 *
 * <p>Failure modes:
 * <ul>
 *   <li>No cookie + authenticated() path → 401
 *       {@code UNAUTHENTICATED} (Spring Security's
 *       {@code anyRequest().authenticated()} rule, plus the
 *       existing {@code GlobalExceptionHandler}).
 *   <li>Bad / expired / malformed cookie → leave the
 *       {@code SecurityContextHolder} empty; the same 401 path
 *       runs.
 *   <li>PLATFORM cookie to a company path → 403
 *       {@code FORBIDDEN_SCOPE} (we throw the
 *       {@code AuthenticationException} directly so the
 *       exception handler produces the right envelope).
 * </ul>
 */
@Component
public class AuthenticationFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(AuthenticationFilter.class);

    private static final String ACCESS_TOKEN_COOKIE = "access_token";

    private final JwtService jwtService;

    public AuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String rawCookie = extractAccessTokenCookie(req);
        if (rawCookie == null || rawCookie.isBlank()) {
            // No cookie — leave the context empty. The
            // SecurityConfig rule `.anyRequest().authenticated()`
            // produces a 401 UNAUTHENTICATED when the request
            // reaches a non-permitAll path.
            chain.doFilter(req, res);
            return;
        }

        ParsedToken token;
        try {
            token = jwtService.parseAndVerify(rawCookie);
        } catch (JwtException ex) {
            // Bad signature, expired, malformed. Same outcome
            // as no cookie: leave the context empty and let the
            // security chain produce a 401. We do NOT throw
            // because we don't want to leak the parse-failure
            // reason to the client (an attacker probing the
            // endpoint should not learn "this token expired 5
            // minutes ago" vs "this token is malformed" — the
            // spec requires a uniform 401).
            LOG.debug("Rejected access_token cookie: {}", ex.getMessage());
            chain.doFilter(req, res);
            return;
        }

        // Cross-scope guard: a PLATFORM cookie presented to a
        // company path is a security event. PR7 will add the
        // symmetric /api/v1/platform/** handler; for now the
        // company path is the only authenticated surface.
        if (token.scope() == TokenScope.PLATFORM
                && req.getRequestURI().startsWith("/api/v1/")
                && !req.getRequestURI().startsWith("/api/v1/platform/")) {
            throw new AuthenticationException(
                    ErrorCode.FORBIDDEN_SCOPE, Map.of("reason", "PLATFORM token presented to a COMPANY path"));
        }

        // All clear — set the principal.
        JwtAuthentication auth = JwtAuthentication.create(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
        try {
            chain.doFilter(req, res);
        } finally {
            // Clear the context to avoid leaking the principal
            // into the next request in the same thread (defense
            // in depth; Spring Security's SecurityContextHolder
            // is THREAD_LOCAL by default and Tomcat reuses
            // threads).
            SecurityContextHolder.clearContext();
        }
    }

    private static String extractAccessTokenCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (Objects.equals(ACCESS_TOKEN_COOKIE, c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
