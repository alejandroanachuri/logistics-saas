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
 * {@code Cookie} header, filtered by the cookie's {@code Path}
 * attribute per RFC 6265 §5.1.4 (a cookie's Path must be a
 * prefix of the request URI). This matters when the browser
 * jar holds both the company cookie ({@code Path=/api/v1}) and
 * the platform cookie ({@code Path=/api/v1/platform}) — for
 * example after a user logged into both surfaces. A naive
 * "first cookie by name" read would hand the platform cookie
 * to the company rehydrator and trigger 403
 * {@code FORBIDDEN_SCOPE} on cold-boot requests to
 * {@code /api/v1/auth/me}. When multiple cookies match, the
 * one with the longest matching Path wins (RFC 6265 §5.4
 * most-specific-match rule), so the platform cookie wins on
 * {@code /api/v1/platform/**} requests.
 *
 * <p>A PLATFORM cookie presented to a company path still
 * returns 403 {@code FORBIDDEN_SCOPE} per spec
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

    /**
     * Picks the {@code access_token} cookie whose {@code Path}
     * matches the request URI. Multiple {@code access_token}
     * cookies can be in the browser jar — one per scope
     * (company {@code Path=/api/v1}, platform
     * {@code Path=/api/v1/platform}). RFC 6265 §5.1.4 says a
     * cookie's Path must be a prefix of the request URI for
     * the cookie to apply; we re-apply that rule here to pick
     * the right one. When multiple cookies apply (e.g. both
     * {@code /api/v1} and {@code /api/v1/platform} for a
     * request under {@code /api/v1/platform/**}), §5.4 says the
     * most specific Path wins — we pick by longest Path.
     *
     * <p>Package-private to allow direct unit testing without
     * standing up a full filter chain.
     */
    static String extractAccessTokenCookieForTest(HttpServletRequest req) {
        return extractAccessTokenCookie(req);
    }

    private static String extractAccessTokenCookie(HttpServletRequest req) {
        Cookie[] cookies = req.getCookies();
        if (cookies == null) {
            return null;
        }
        String requestPath = req.getRequestURI();
        String bestValue = null;
        int bestPathLength = -1;
        boolean bestHasPath = false;
        for (Cookie c : cookies) {
            if (!Objects.equals(ACCESS_TOKEN_COOKIE, c.getName())) {
                continue;
            }
            String cookiePath = c.getPath();
            boolean hasPath = cookiePath != null && !cookiePath.isEmpty();
            boolean applies;
            if (!hasPath) {
                // No Path attribute = applies to every request URI
                // (RFC 6265 §5.1.4 default-path rule). Kept as a
                // fallback only; a Path-bearing cookie always
                // outranks a no-path one.
                applies = true;
            } else {
                applies = requestPath.equals(cookiePath)
                        || requestPath.startsWith(cookiePath + "/");
            }
            if (!applies) {
                continue;
            }
            // Longest matching Path wins (RFC 6265 §5.4 most-specific-match).
            // A no-path cookie is only used when no Path-bearing cookie applies.
            if (hasPath) {
                if (!bestHasPath || cookiePath.length() > bestPathLength) {
                    bestValue = c.getValue();
                    bestPathLength = cookiePath.length();
                    bestHasPath = true;
                }
            } else if (!bestHasPath) {
                bestValue = c.getValue();
            }
        }
        return bestValue;
    }
}
