package ar.com.logistics.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code app.jwt.*} block from {@code application.yml}.
 *
 * <p>Bound by Spring Boot's {@code @ConfigurationProperties} scan; the
 * {@code JwtService} bean consumes it at construction time.
 *
 * <p>The secret is stored as a String (base64-encoded) and decoded by
 * {@code JwtService} because Spring Boot 4 / Jackson 3 do not auto-bind
 * a base64 string to {@code byte[]} via the default property
 * converters — the explicit {@code Base64.getDecoder().decode()}
 * call is explicit and testable.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer, String audience) {}
