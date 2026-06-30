package ar.com.logistics.config;

import ar.com.logistics.auth.filter.AuthenticationFilter;
import ar.com.logistics.auth.jwt.JwtProperties;
import ar.com.logistics.common.cookie.CookieWriter;
import ar.com.logistics.common.ratelimit.RateLimitFilter;
import ar.com.logistics.common.ratelimit.RateLimitProperties;
import ar.com.logistics.common.security.SecurityHeadersFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * First real Spring Security configuration. PR1d left the auto-config
 * default (which logs an in-memory password and warns at startup);
 * PR2 ships the explicit {@code SecurityFilterChain} that PR3/PR4 will
 * hang the auth filter off of.
 *
 * <p>Decisions (mirrors PRD lines880-899 + ADR-0005):
 * <ul>
 * <li>CSRF off — API is JSON-only and tokens are not in URLs.</li>
 * <li>HTTP basic + form login off — auth is via cookie only.</li>
 * <li>Sessions stateless — every request is authenticated by JWT.</li>
 * <li>CORS allows {@code http://localhost:4200} with credentials
 * (Angular dev server). Production origins are added per
 * environment.</li>
 * </ul>
 *
 * <p>The filter that translates the {@code access_token} cookie into
 * a Spring {@code Authentication} lives in PR4 (authentication filter).
 * For PR2 we expose the filter chain shell so PR3 can wire its
 * registration endpoint without fighting the default security.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableAsync
@EnableConfigurationProperties({JwtProperties.class, CookieWriter.CookieProperties.class, RateLimitProperties.class})
public class SecurityConfig {

    /** BCrypt strength12 per design §7 (matches PRD line856). */
    private static final int BCRYPT_STRENGTH = 12;

    private final SecurityHeadersFilter securityHeadersFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AuthenticationFilter authenticationFilter;

    public SecurityConfig(
            SecurityHeadersFilter securityHeadersFilter,
            RateLimitFilter rateLimitFilter,
            AuthenticationFilter authenticationFilter) {
        this.securityHeadersFilter = securityHeadersFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.authenticationFilter = authenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // The order matters here:
        //   1. SecurityHeadersFilter — adds the four baseline security
        //      headers to every response, including 401s.
        //   2. RateLimitFilter — Bucket4j on the anonymous endpoints
        //      (register, login, availability). Sits BEFORE the
        //      authentication filter so abusive traffic is dropped
        //      before reaching the auth code.
        //   3. AuthenticationFilter — translates the access_token
        //      cookie into a Spring Authentication. Must run BEFORE
        //      any controller that reads @AuthenticationPrincipal.
        http.addFilterBefore(
                        securityHeadersFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        rateLimitFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        authenticationFilter,
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .csrf(csrf -> csrf.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Public registration + login + reference + availability endpoints.
                        .requestMatchers(
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/platform/auth/login",
                                "/api/v1/reference/**",
                                "/api/v1/tenants/me/slug-availability",
                                "/api/v1/tenants/me/cuit-availability",
                                "/api/v1/tenants/me/username-availability",
                                // Public tracking portal — no cookie, no tenant
                                // binding. AuthenticationFilter and RateLimitFilter
                                // enforce IP-based rate-limit + early-return.
                                "/api/v1/public/**",
                                "/actuator/health")
                        .permitAll()
                        // Everything else requires a valid access_token
                        // cookie; the AuthenticationFilter above populates
                        // the SecurityContextHolder. PR5+ endpoints land
                        // here.
                        .anyRequest()
                        .authenticated())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()));
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.addAllowedOrigin("http://localhost:4200");
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * Executor for {@code @Async} methods (currently
     * {@link ar.com.logistics.common.audit.AuditLogger#logAsync}).
     * Sized small because async tasks here are tiny DB inserts; bump
     * if we add heavier async work in later PRs.
     */
    @Bean(name = "applicationTaskExecutor")
    public ThreadPoolTaskExecutor applicationTaskExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(4);
        exec.setQueueCapacity(100);
        exec.setThreadNamePrefix("logistics-async-");
        exec.initialize();
        return exec;
    }
}
