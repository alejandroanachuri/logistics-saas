package ar.com.logistics.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds the four baseline security headers to every response. Wired
 * in {@link ar.com.logistics.config.SecurityConfig} BEFORE the
 * authentication filter so unauthenticated 401s also get the
 * headers (defense in depth — even an attacker probing an
 * endpoint that returns 401 should see the headers so they don't
 * learn anything about the response shape from a side channel).
 *
 * <ul>
 *   <li>{@code X-Content-Type-Options: nosniff} — block MIME
 *       sniffing on responses (defense against content-type
 *       confusion attacks).</li>
 *   <li>{@code X-Frame-Options: DENY} — block framing entirely
 *       (the API never renders inside an iframe; this is belt-
 *       and-braces for the rare case a stack trace is leaked
 *       into an HTML error page).</li>
 *   <li>{@code Content-Security-Policy: default-src 'none';
 *       frame-ancestors 'none'} — equivalent CSP for an API
 *       surface (no inline scripts, no remote anything).</li>
 *   <li>{@code Referrer-Policy: no-referrer} — the API never
 *       embeds a reason to leak the referrer to a third-party
 *       host.</li>
 * </ul>
 *
 * <p>CSRF is handled separately by the Spring Security filter
 * chain (currently disabled for the API, but the headers stay
 * in place for any future page that might render in a browser
 * context).
 */
@Component
@Order(0)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    private static final String CSP = "default-src 'none'; frame-ancestors 'none'";

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Content-Security-Policy", CSP);
        res.setHeader("Referrer-Policy", "no-referrer");
        chain.doFilter(req, res);
    }
}
