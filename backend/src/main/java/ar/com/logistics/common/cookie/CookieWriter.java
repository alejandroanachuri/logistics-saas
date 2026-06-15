package ar.com.logistics.common.cookie;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Centralised writer for the access_token and refresh_token cookies.
 * Per ADR-0005, the cookie paths are scoped so a browser will not send
 * a platform-scope cookie to a company endpoint or vice versa:
 *
 * <ul>
 * <li>Company access token: {@code Path=/api/v1}</li>
 * <li>Company refresh token: {@code Path=/api/v1/auth}</li>
 * <li>Platform access token: {@code Path=/api/v1/platform}</li>
 * <li>Platform refresh token: {@code Path=/api/v1/platform/auth}</li>
 * </ul>
 *
 * <p>All cookies are {@code HttpOnly}. {@code Secure} is read from
 * {@code app.cookies.secure} (default {@code true}; the dev profile
 * overrides it to {@code false} so the browser accepts it on
 * {@code http://localhost}). {@code SameSite} defaults to {@code Strict}.
 *
 * <p>Implementation note: we use {@link ResponseCookie#toString()} as the
 * {@code Set-Cookie} header value because {@code ResponseCookie} is
 * Spring's immutable builder that supports {@code SameSite} (which
 * {@code jakarta.servlet.http.Cookie} does not).
 */
@Component
public class CookieWriter {

    public static final String COMPANY = "COMPANY";
    public static final String PLATFORM = "PLATFORM";

    private final boolean secure;
    private final String sameSite;

    public CookieWriter(CookieProperties props) {
        this.secure = props.secure();
        this.sameSite = props.sameSite();
    }

    public void writeAccessToken(HttpServletResponse response, String scope, String token, Duration ttl) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(scope, "access_token", token, pathForAccess(scope), ttl)
                        .toString());
    }

    public void writeRefreshToken(HttpServletResponse response, String scope, String token, Duration ttl) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(scope, "refresh_token", token, pathForRefresh(scope), ttl)
                        .toString());
    }

    public void clearAccessToken(HttpServletResponse response, String scope) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(scope, "access_token", "", pathForAccess(scope), Duration.ZERO)
                        .toString());
    }

    public void clearRefreshToken(HttpServletResponse response, String scope) {
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                buildCookie(scope, "refresh_token", "", pathForRefresh(scope), Duration.ZERO)
                        .toString());
    }

    private ResponseCookie buildCookie(String scope, String name, String value, String path, Duration ttl) {
        ResponseCookie.ResponseCookieBuilder b = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(path);
        b.maxAge(ttl);
        return b.build();
    }

    private static String pathForAccess(String scope) {
        return COMPANY.equals(scope) ? "/api/v1" : "/api/v1/platform";
    }

    private static String pathForRefresh(String scope) {
        return COMPANY.equals(scope) ? "/api/v1/auth" : "/api/v1/platform/auth";
    }

    @ConfigurationProperties(prefix = "app.cookies")
    public record CookieProperties(boolean secure, String sameSite) {}
}
