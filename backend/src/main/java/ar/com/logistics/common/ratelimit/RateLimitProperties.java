package ar.com.logistics.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed knobs for {@code app.rate-limit.*} (mirrored from
 * {@code application.yml}). Bound by
 * {@link org.springframework.boot.context.properties.EnableConfigurationProperties}
 * in {@link ar.com.logistics.config.SecurityConfig}.
 *
 * <p>Defaults mirror the spec:
 * <ul>
 *   <li>5 / hour on {@code POST /api/v1/auth/register}</li>
 *   <li>10 / minute on {@code POST /api/v1/auth/login} and
 *       {@code POST /api/v1/platform/auth/login}</li>
 *   <li>30 / minute on the three availability endpoints</li>
 *   <li>30 / hour / IP on the public tracking portal
 *       ({@code GET /api/v1/public/track/{lgstid}} — PRD etapa-3
 *       §9.1).</li>
 * </ul>
 *
 * <p>Setting {@code enabled = false} bypasses the filter entirely
 * (useful for the local dev profile and for tests). When disabled
 * every request is allowed through regardless of budget.
 */
@ConfigurationProperties(prefix = "app.rate-limit")
public record RateLimitProperties(
        boolean enabled, int registerPerHour, int loginPerMinute, int availabilityPerMinute, int publicTrackPerHour) {}
