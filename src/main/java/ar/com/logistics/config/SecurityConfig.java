package ar.com.logistics.config;

import ar.com.logistics.auth.jwt.JwtProperties;
import ar.com.logistics.common.cookie.CookieWriter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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
@EnableAsync
@EnableConfigurationProperties({JwtProperties.class, CookieWriter.CookieProperties.class})
public class SecurityConfig {

    /** BCrypt strength12 per design §7 (matches PRD line856). */
    private static final int BCRYPT_STRENGTH = 12;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(BCRYPT_STRENGTH);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
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
                                "/actuator/health")
                        .permitAll()
                        // Everything else (including /api/v1/platform/**) is authenticated.
                        // The actual filter that maps cookies to Authentication lands in PR4.
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
