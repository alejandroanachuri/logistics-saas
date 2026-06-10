package ar.com.logistics.auth.jwt;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code app.jwt.*} block from {@code application.yml}.
 *
 * <p>Bound by Spring Boot's {@code @ConfigurationProperties} scan; the
 * {@code JwtService} bean consumes it at construction time.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        byte[] secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer, String audience) {}
