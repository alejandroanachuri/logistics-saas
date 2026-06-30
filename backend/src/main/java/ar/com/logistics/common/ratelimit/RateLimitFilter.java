package ar.com.logistics.common.ratelimit;

import ar.com.logistics.common.audit.AuditEvent;
import ar.com.logistics.common.audit.AuditLogger;
import ar.com.logistics.common.exception.ErrorCode;
import ar.com.logistics.common.exception.ErrorEnvelope;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP Bucket4j rate limit on the four anonymous endpoints
 * the spec calls out. In-memory {@code ConcurrentHashMap} of
 * {@code ip -> Bucket} — single-process v1, per spec open_gaps
 * (Redis is the v2 follow-up).
 *
 * <p>Per spec:
 * <ul>
 *   <li>5 / hour on {@code POST /api/v1/auth/register}
 *   <li>10 / minute on {@code POST /api/v1/auth/login} and
 *       {@code POST /api/v1/platform/auth/login}
 *   <li>30 / minute on the three availability endpoints
 *   <li>30 / hour / IP on the public tracking portal
 *       {@code GET /api/v1/public/track/{lgstid}} (PRD etapa-3 §9.1)
 * </ul>
 *
 * <p>On consumption: response carries the standard
 * {@code X-RateLimit-Remaining: <tokens>} header (RFC draft
 * standard). On exhaustion: {@code 429 RATE_LIMIT_EXCEEDED}
 * with {@code Retry-After} (seconds) and a
 * {@code RATE_LIMIT_EXCEEDED} audit row.
 *
 * <p>The filter is bypassed when {@code app.rate-limit.enabled = false}
 * so the dev profile and tests can run without budget overhead.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties props;
    private final AuditLogger auditLogger;
    private final tools.jackson.databind.ObjectMapper objectMapper;
    private final Map<String, Bucket> registerBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> loginBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> availabilityBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> publicTrackBuckets = new ConcurrentHashMap<>();

    public RateLimitFilter(
            RateLimitProperties props, AuditLogger auditLogger, tools.jackson.databind.ObjectMapper objectMapper) {
        this.props = props;
        this.auditLogger = auditLogger;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        if (!props.enabled()) {
            chain.doFilter(req, res);
            return;
        }

        Bucket bucket = bucketFor(req);
        if (bucket == null) {
            chain.doFilter(req, res);
            return;
        }

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        res.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0, probe.getRemainingTokens())));
        if (probe.isConsumed()) {
            chain.doFilter(req, res);
        } else {
            long waitSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
            res.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(waitSeconds));
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ErrorEnvelope env = ErrorEnvelope.of(
                    ErrorCode.RATE_LIMIT_EXCEEDED,
                    ErrorCode.RATE_LIMIT_EXCEEDED.defaultMessage(),
                    Map.of("retryAfterSeconds", waitSeconds));
            objectMapper.writeValue(res.getOutputStream(), env);
            auditLogger.logAsync(new AuditEvent(
                    "RATE_LIMIT_EXCEEDED",
                    null,
                    AuditEvent.UserScope.ANONYMOUS,
                    null,
                    null,
                    null,
                    Map.of(
                            "path", req.getRequestURI(),
                            "ip", clientIp(req),
                            "retryAfterSeconds", waitSeconds)));
        }
    }

    /**
     * Returns the bucket for the request's path, or {@code null}
     * if the path is not rate-limited.
     */
    private Bucket bucketFor(HttpServletRequest req) {
        String path = req.getRequestURI();
        String method = req.getMethod();
        String ip = clientIp(req);

        if ("POST".equals(method) && "/api/v1/auth/register".equals(path)) {
            return registerBuckets.computeIfAbsent(ip, k -> newBucket(props.registerPerHour(), Duration.ofHours(1)));
        }
        if ("POST".equals(method)
                && ("/api/v1/auth/login".equals(path) || "/api/v1/platform/auth/login".equals(path))) {
            return loginBuckets.computeIfAbsent(ip, k -> newBucket(props.loginPerMinute(), Duration.ofMinutes(1)));
        }
        if ("GET".equals(method)
                && (path.startsWith("/api/v1/tenants/me/slug-availability")
                        || path.startsWith("/api/v1/tenants/me/cuit-availability")
                        || path.startsWith("/api/v1/tenants/me/username-availability"))) {
            return availabilityBuckets.computeIfAbsent(
                    ip, k -> newBucket(props.availabilityPerMinute(), Duration.ofMinutes(1)));
        }
        // Public tracking portal: same keying rule (per client IP) but
        // hourly bucket — abused scrapers hitting the same {lgstid}
        // pattern would otherwise burn the 30/min availability bucket
        // on the wrong endpoint class. PRD etapa-3 §9.1 calls for 30
        // req / IP / hora; mirror it from RateLimitProperties.
        if ("GET".equals(method) && path.startsWith("/api/v1/public/track/")) {
            return publicTrackBuckets.computeIfAbsent(
                    ip, k -> newBucket(props.publicTrackPerHour(), Duration.ofHours(1)));
        }
        return null;
    }

    private static Bucket newBucket(int capacity, Duration period) {
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, period)
                .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private static String clientIp(HttpServletRequest req) {
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            return fwd.split(",")[0].trim();
        }
        return Objects.requireNonNullElse(req.getRemoteAddr(), "unknown");
    }
}
