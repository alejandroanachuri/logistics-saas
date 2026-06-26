package ar.com.logistics.common.jsonview;

import ar.com.logistics.auth.security.JwtAuthentication;
import java.util.Set;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Global response advice that selects the Jackson serialization view for
 * outgoing responses based on the current user's roles.
 *
 * <p>Per PRD §10.1, role-based field-level security:
 * <ul>
 *   <li>{@code COMPANY_ADMIN} or {@code COMPANY_OPERATOR} → {@link Views.Default}
 *       (fields tagged with {@code @JsonView(Views.Default.class)} are
 *       INCLUDED — sensitive fields like {@code dni} / {@code cuitCuil}
 *       are visible).</li>
 *   <li>{@code COMPANY_DRIVER} or {@code COMPANY_VIEWER} → {@link Views.Masked}
 *       (those fields are EXCLUDED).</li>
 *   <li>Unauthenticated (no JWT in {@link SecurityContextHolder}, e.g.
 *       {@code /api/v1/public/**}) → {@link Views.Masked} (defense in
 *       depth: even if a sensitive object reaches a public endpoint, its
 *       sensitive fields are stripped).</li>
 * </ul>
 *
 * <p>The advice wraps the response body in a
 * {@link MappingJacksonValue} with the chosen view class. Spring's
 * {@code AbstractJackson2HttpMessageConverter} recognizes that wrapper
 * and applies the view filter during serialization. No controller code
 * has to change — the view is selected globally for every JSON response.
 */
@ControllerAdvice
public class JsonViewResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * Roles that see the full {@link Views.Default} (sensitive fields
     * included). Defensive copy at class load via {@link Set#of(Object[])}
     * which throws on duplicates.
     */
    private static final Set<String> FULL_VISIBILITY_ROLES = Set.of("COMPANY_ADMIN", "COMPANY_OPERATOR");

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Apply to all responses; the wrapping is cheap (a single object
        // allocation) and ensures consistent field-level security across
        // every endpoint that returns JSON.
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        Class<?> viewClass = resolveViewClass();
        MappingJacksonValue wrapped = new MappingJacksonValue(body);
        wrapped.setSerializationView(viewClass);
        return wrapped;
    }

    /**
     * Decide which view applies to the current request based on the
     * authenticated user's roles. Visible for testing.
     *
     * <ul>
     *   <li>Unauthenticated (no auth OR {@code !isAuthenticated()}) →
     *       {@link Views.Masked}.</li>
     *   <li>{@link JwtAuthentication} whose roles include any
     *       {@code FULL_VISIBILITY_ROLES} entry → {@link Views.Default}.</li>
     *   <li>{@link JwtAuthentication} with only restricted roles
     *       (DRIVER / VIEWER) → {@link Views.Masked}.</li>
     *   <li>Any other {@link Authentication} (anonymous, etc.) →
     *       {@link Views.Masked} (defense in depth).</li>
     * </ul>
     */
    private Class<?> resolveViewClass() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Views.Masked.class;
        }
        if (auth instanceof JwtAuthentication jwt) {
            for (String role : jwt.getRoles()) {
                if (FULL_VISIBILITY_ROLES.contains(role)) {
                    return Views.Default.class;
                }
            }
            return Views.Masked.class;
        }
        return Views.Masked.class;
    }
}
