package ar.com.logistics.auth.security;

import ar.com.logistics.auth.jwt.JwtService;
import ar.com.logistics.auth.jwt.JwtService.ParsedToken;
import ar.com.logistics.auth.jwt.JwtService.TokenScope;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Spring Security {@code Authentication} backed by a verified
 * {@code access_token} JWT. The principal is the typed
 * {@link ParsedToken} (a record) so downstream code can do
 * {@code @AuthenticationPrincipal ParsedToken p} without going
 * through {@code SecurityContextHolder}.
 *
 * <p>Authorities are derived from claims:
 * <ul>
 *   <li>{@code ROLE_<role>} — e.g. {@code ROLE_COMPANY_ADMIN}
 *   <li>{@code SCOPE_<scope>} — e.g. {@code SCOPE_COMPANY} or
 *       {@code SCOPE_PLATFORM}
 * </ul>
 * Both forms are kept so {@code hasRole(...)} and
 * {@code hasAuthority("SCOPE_COMPANY")} both work in declarative
 * security annotations (future PR).
 *
 * <p>Created by
 * {@link ar.com.logistics.auth.filter.AuthenticationFilter} after a
 * successful {@link JwtService#parseAndVerify(String)}. The filter
 * does NOT call the constructor directly — it sets the token via
 * {@link #create(ParsedToken)} and the
 * {@link org.springframework.security.core.context.SecurityContextHolder}.
 */
public final class JwtAuthentication extends AbstractAuthenticationToken {

    private final ParsedToken principal;

    private JwtAuthentication(ParsedToken principal, List<GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        setAuthenticated(true);
    }

    /**
     * Build a {@code JwtAuthentication} from a verified
     * {@link ParsedToken}. The role claim (e.g.
     * {@code COMPANY_ADMIN}) becomes {@code ROLE_COMPANY_ADMIN};
     * the scope claim (e.g. {@code COMPANY}) becomes
     * {@code SCOPE_COMPANY}. The principal is the typed
     * {@link ParsedToken} record.
     */
    public static JwtAuthentication create(ParsedToken token) {
        List<GrantedAuthority> authorities = new ArrayList<>(2);
        if (token.role() != null && !token.role().isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + token.role()));
        }
        if (token.scope() == TokenScope.COMPANY) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_COMPANY"));
        } else if (token.scope() == TokenScope.PLATFORM) {
            authorities.add(new SimpleGrantedAuthority("SCOPE_PLATFORM"));
        }
        return new JwtAuthentication(token, List.copyOf(authorities));
    }

    @Override
    public Object getCredentials() {
        // Tokens are bearer credentials; we don't re-expose the raw
        // JWT string after verification. Downstream code that needs
        // the claims should read the principal.
        return "";
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    /** Convenience: the typed claims as a {@link ParsedToken}. */
    public ParsedToken parsed() {
        return principal;
    }

    @Override
    public String getName() {
        return principal == null ? "" : principal.subject().toString();
    }

    /**
     * Convenience for the company path: returns the tenant id from
     * the JWT, or {@code null} for a platform-scope token.
     */
    public UUID tenantIdOrNull() {
        return principal == null ? null : principal.tenantId();
    }
}
