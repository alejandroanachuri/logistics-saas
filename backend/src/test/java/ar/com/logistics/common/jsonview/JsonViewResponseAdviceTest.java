package ar.com.logistics.common.jsonview;

import static org.assertj.core.api.Assertions.assertThat;

import ar.com.logistics.auth.jwt.JwtService.ParsedToken;
import ar.com.logistics.auth.jwt.JwtService.TokenScope;
import ar.com.logistics.auth.security.JwtAuthentication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link JsonViewResponseAdvice}.
 *
 * <p>The advice selects a Jackson {@link MappingJacksonValue} view based on
 * the current user's roles, per PRD §10.1:
 * <ul>
 *   <li>{@code COMPANY_ADMIN} or {@code COMPANY_OPERATOR} → {@link Views.Default}
 *       (sensitive fields INCLUDED)</li>
 *   <li>{@code COMPANY_DRIVER} or {@code COMPANY_VIEWER} → {@link Views.Masked}
 *       (sensitive fields EXCLUDED)</li>
 *   <li>Unauthenticated (no JWT in SecurityContext, e.g. /api/v1/public/**) →
 *       {@link Views.Masked} (defense in depth)</li>
 * </ul>
 *
 * <p>The tests stub the {@link SecurityContextHolder} directly and call
 * {@link JsonViewResponseAdvice#beforeBodyWrite} with {@code null} for the
 * HTTP-shaped arguments (the advice doesn't read them), asserting the
 * returned wrapper carries the right view class. No Spring context is
 * required.
 */
class JsonViewResponseAdviceTest {

    private JsonViewResponseAdvice advice;

    @BeforeEach
    void setUp() {
        advice = new JsonViewResponseAdvice();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("COMPANY_ADMIN → Default view (sensitive fields visible)")
    void adminRole_returnsDefaultView() {
        setAuthenticatedUser(List.of("COMPANY_ADMIN"));

        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        MappingJacksonValue mjv = (MappingJacksonValue) wrapped;
        assertThat(mjv.getSerializationView()).isEqualTo(Views.Default.class);
        assertThat(mjv.getValue()).isSameAs(body);
    }

    @Test
    @DisplayName("COMPANY_OPERATOR → Default view (sensitive fields visible)")
    void operatorRole_returnsDefaultView() {
        setAuthenticatedUser(List.of("COMPANY_OPERATOR"));

        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        assertThat(((MappingJacksonValue) wrapped).getSerializationView()).isEqualTo(Views.Default.class);
    }

    @Test
    @DisplayName("COMPANY_DRIVER → Masked view (sensitive fields excluded)")
    void driverRole_returnsMaskedView() {
        setAuthenticatedUser(List.of("COMPANY_DRIVER"));

        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        assertThat(((MappingJacksonValue) wrapped).getSerializationView()).isEqualTo(Views.Masked.class);
    }

    @Test
    @DisplayName("COMPANY_VIEWER → Masked view (sensitive fields excluded)")
    void viewerRole_returnsMaskedView() {
        setAuthenticatedUser(List.of("COMPANY_VIEWER"));

        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        assertThat(((MappingJacksonValue) wrapped).getSerializationView()).isEqualTo(Views.Masked.class);
    }

    @Test
    @DisplayName("Unauthenticated → Masked view (defense in depth)")
    void unauthenticated_returnsMaskedView() {
        // SecurityContextHolder is empty (no @BeforeEach set it).
        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        assertThat(((MappingJacksonValue) wrapped).getSerializationView()).isEqualTo(Views.Masked.class);
    }

    @Test
    @DisplayName("Multiple roles including COMPANY_ADMIN → Default view (any full-visibility role wins)")
    void multipleRolesWithAdmin_returnsDefaultView() {
        setAuthenticatedUser(List.of("COMPANY_VIEWER", "COMPANY_ADMIN", "COMPANY_DRIVER"));

        Object body = new Object();
        Object wrapped = advice.beforeBodyWrite(body, null, MediaType.APPLICATION_JSON, null, null, null);

        assertThat(wrapped).isInstanceOf(MappingJacksonValue.class);
        assertThat(((MappingJacksonValue) wrapped).getSerializationView()).isEqualTo(Views.Default.class);
    }

    /**
     * Build a {@link JwtAuthentication} with the given role names and install
     * it in the {@link SecurityContextHolder}. Uses the real factory so the
     * advice's {@code instanceof JwtAuthentication} branch is exercised end to
     * end (parsing + authority derivation), mirroring production.
     */
    private static void setAuthenticatedUser(List<String> roles) {
        ParsedToken token = new ParsedToken(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "acme",
                roles.isEmpty() ? null : roles.get(0),
                roles,
                TokenScope.COMPANY,
                "logistics-saas",
                "logistics-saas-web");
        Authentication auth = JwtAuthentication.create(token);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
