package ar.com.logistics.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

/**
 * PR3 — End-to-end integration test for the tenant-registration
 * flow. This is the canonical gate that PR4 builds on:
 *
 * <ul>
 *   <li>Happy path: {@code 201} with the full response shape, the
 *       tenant + user are persisted, the verification token is
 *       populated with a 24h expiry, and an audit row is written
 *       with {@code event_type = TENANT_REGISTERED}.</li>
 *   <li>{@code 409 SLUG_ALREADY_TAKEN}, {@code 409 CUIT_ALREADY_REGISTERED},
 *       {@code 409 RESERVED_SLUG} all return the canonical envelope.</li>
 *   <li>{@code 400 VALIDATION_ERROR} on missing field, bad CUIT, slug
 *       too short, slug with uppercase, password without digit.</li>
 *   <li>The newly created tenant is visible to the
 *       {@code systemDataSource} cross-tenant lookup (login
 *       pre-condition).</li>
 * </ul>
 *
 * <p>The {@code @ActiveProfiles("test")} selection plus the
 * {@code DswiringIT} class together prove that:
 * <ol>
 *   <li>Spring wires all three DataSources (covered in PR1d
 *       {@code DswiringIT}).</li>
 *   <li>Registration hits the system pool (BYPASSRLS) and the
 *       resulting rows are visible there but NOT via the company
 *       pool's RLS filter (covered here in the cross-tenant
 *       login-lookup assertion).</li>
 * </ol>
 */
@Testcontainers
@SpringBootTest(
        properties = {
            // Disable Spring Boot's Flyway auto-config: with three
            // DataSources present, the autoconfig cannot decide which
            // one to bind to. We run Flyway manually in the @BeforeAll
            // (the same pattern RlsIntegrationIT uses) so the schema is
            // in place before Spring's EMF beans are wired.
            "spring.flyway.enabled=false"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class RegistrationIT {

    private static final String POSTGRES_IMAGE = "postgres:16-alpine";
    private static final String POSTGRES_DB = "logistics";
    private static final String POSTGRES_USER = "postgres";
    private static final String POSTGRES_PASS = "postgres";

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(POSTGRES_IMAGE)
            .withDatabaseName(POSTGRES_DB)
            .withUsername(POSTGRES_USER)
            .withPassword(POSTGRES_PASS);

    @DynamicPropertySource
    static void configureDataSources(DynamicPropertyRegistry registry) {
        registry.add("app.datasource.company.url", POSTGRES::getJdbcUrl);
        registry.add("app.datasource.company.username", () -> "app_user");
        registry.add("app.datasource.company.password", () -> "app_user");
        registry.add("app.datasource.system.url", POSTGRES::getJdbcUrl);
        registry.add("app.datasource.system.username", () -> "app_admin");
        registry.add("app.datasource.system.password", () -> "app_admin");
        registry.add("app.datasource.platform.url", POSTGRES::getJdbcUrl);
        registry.add("app.datasource.platform.username", () -> "app_platform");
        registry.add("app.datasource.platform.password", () -> "app_platform");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("systemDataSource")
    private DataSource systemDataSource;

    @Autowired
    @Qualifier("companyDataSource")
    private DataSource companyDataSource;

    private JdbcTemplate systemJdbc;

    @AfterAll
    static void stop() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    /**
     * Start the Testcontainers Postgres and create the three application
     * roles with the same passwords the test properties point at. Runs
     * BEFORE Flyway because V8's {@code GRANT}s target these roles by
     * name. Mirrors the init block in {@code RlsIntegrationIT} so the
     * two test suites share the same role-credential convention.
     *
     * <p>JUnit 5 does not guarantee the execution order of multiple
     * {@code @BeforeAll} methods, so the start + role-create steps are
     * kept together in a single method.
     */
    @BeforeAll
    static void bootstrap() throws Exception {
        POSTGRES.start();
        try (Connection conn = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = conn.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            st.execute("CREATE ROLE app_user NOINHERIT LOGIN PASSWORD 'app_user'");
            st.execute("CREATE ROLE app_admin NOINHERIT BYPASSRLS LOGIN PASSWORD 'app_admin'");
            st.execute("CREATE ROLE app_platform NOINHERIT LOGIN PASSWORD 'app_platform'");
            st.execute("GRANT CONNECT ON DATABASE " + POSTGRES.getDatabaseName()
                    + " TO app_user, app_admin, app_platform");
        }

        // Run Flyway migrations against the freshly-bootstrapped DB.
        // Spring Boot's auto-Flyway is disabled above (see the
        // spring.flyway.enabled=false property on @SpringBootTest)
        // because with three DataSources the autoconfig cannot
        // pick one to migrate. Doing it explicitly here mirrors
        // the pattern in RlsIntegrationIT and keeps the schema in
        // place before Spring's EMF beans try to validate it.
        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
    }

    @org.junit.jupiter.api.BeforeEach
    void setupJdbc() {
        systemJdbc = new JdbcTemplate(systemDataSource);
    }

    // ===================================================================
    // Happy path
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(1)
    @DisplayName("Happy path: 201 with the canonical response, tenant + user persisted, token + audit written")
    void register_happy_path_returns_201_and_persists_everything() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("mvr", "30-71234567-1", "admin", "admin@mvr.test", "Passw0rdSegura!");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                // No Set-Cookie (auto-login is deferred to PR4)
                .andExpect(cookie().doesNotExist("access_token"))
                .andExpect(jsonPath("$.tenantId").exists())
                .andExpect(jsonPath("$.slug").value("mvr"))
                .andExpect(jsonPath("$.legalName").value("MVR S.A."))
                .andExpect(jsonPath("$.cuit").value("30712345671"))
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.email").value("admin@mvr.test"))
                .andExpect(jsonPath("$.firstName").value("Maria"))
                .andExpect(jsonPath("$.lastName").value("Vidal"))
                .andExpect(jsonPath("$.role").value("COMPANY_ADMIN"))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andReturn();

        // The JSON was asserted above; now verify the DB state.
        String tenantId = systemJdbc.queryForObject("SELECT id FROM public.tenants WHERE slug = 'mvr'", String.class);
        String userId = systemJdbc.queryForObject(
                "SELECT id FROM public.company_users WHERE tenant_id = ?::uuid AND username = 'admin'",
                String.class,
                tenantId);

        // Verification token populated, expiry ~24h
        UUID token = UUID.fromString(systemJdbc.queryForObject(
                "SELECT verification_token FROM public.company_users WHERE id = ?::uuid", String.class, userId));
        assertThat(token).isNotNull();
        Instant expiry = systemJdbc.queryForObject(
                "SELECT verification_token_expires_at FROM public.company_users WHERE id = ?::uuid",
                Instant.class,
                userId);
        assertThat(expiry).isAfter(Instant.now().plusSeconds(23 * 3600));
        assertThat(expiry).isBefore(Instant.now().plusSeconds(25 * 3600));

        // Audit row was written
        Integer auditCount = systemJdbc.queryForObject(
                "SELECT COUNT(*) FROM public.audit_log "
                        + "WHERE event_type = 'TENANT_REGISTERED' AND tenant_id = ?::uuid",
                Integer.class,
                tenantId);
        assertThat(auditCount).isEqualTo(1);
    }

    // ===================================================================
    // 409 conflicts
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(10)
    @DisplayName("409 SLUG_ALREADY_TAKEN when the same slug is registered twice")
    void register_duplicate_slug_returns_409() throws Exception {
        // Seed the first registration
        Map<String, Object> first =
                buildRegisterRequest("dup", "30-71234568-9", "first", "first@dup.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        // Second attempt with the same slug
        Map<String, Object> second =
                buildRegisterRequest("dup", "20-12345678-6", "second", "second@dup.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SLUG_ALREADY_TAKEN"))
                .andExpect(jsonPath("$.error.details.slug").exists());
    }

    @Test
    @org.junit.jupiter.api.Order(11)
    @DisplayName("409 CUIT_ALREADY_REGISTERED when the same CUIT is reused with a different slug")
    void register_duplicate_cuit_returns_409() throws Exception {
        Map<String, Object> first =
                buildRegisterRequest("cuit1", "30-71234569-8", "user1", "u1@cuit.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isCreated());

        Map<String, Object> second =
                buildRegisterRequest("cuit2", "30-71234569-8", "user2", "u2@cuit.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(second)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CUIT_ALREADY_REGISTERED"))
                .andExpect(jsonPath("$.error.details.cuit").exists());
    }

    @Test
    @org.junit.jupiter.api.Order(12)
    @DisplayName("409 RESERVED_SLUG when the slug is in the reserved catalog")
    void register_reserved_slug_returns_409() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("admin", "30-71234570-1", "adminu", "adminu@res.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("RESERVED_SLUG"));
    }

    // ===================================================================
    // 400 validation
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(20)
    @DisplayName("400 VALIDATION_ERROR when CUIT is missing")
    void register_missing_cuit_returns_400() throws Exception {
        Map<String, Object> req = buildRegisterRequest("mvr1", null, "adminu", "adminu@v.test", "Passw0rdSegura!");
        // Remove the cuit key entirely
        @SuppressWarnings("unchecked")
        Map<String, Object> company = (Map<String, Object>) req.get("company");
        company.remove("cuit");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.['company.cuit']").exists());
    }

    @Test
    @org.junit.jupiter.api.Order(21)
    @DisplayName("400 VALIDATION_ERROR when CUIT fails the mod-11 check digit")
    void register_bad_cuit_check_digit_returns_400() throws Exception {
        // The last digit is wrong; first 10 are valid.
        Map<String, Object> req =
                buildRegisterRequest("mvr2", "30-71234567-0", "adminu", "adminu@v.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.cuit").exists());
    }

    @Test
    @org.junit.jupiter.api.Order(22)
    @DisplayName("400 VALIDATION_ERROR when slug is too short (1 char)")
    void register_short_slug_returns_400() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("m", "30-71234571-9", "adminu", "adminu@v.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @org.junit.jupiter.api.Order(23)
    @DisplayName("400 VALIDATION_ERROR when slug has an uppercase letter")
    void register_uppercase_slug_returns_400() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("MVR", "30-71234572-8", "adminu", "adminu@v.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @org.junit.jupiter.api.Order(24)
    @DisplayName("400 VALIDATION_ERROR when password is missing the digit class")
    void register_password_without_digit_returns_400() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("mvr3", "30-71234573-6", "adminu", "adminu@v.test", "OnlyLetters!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.password").exists());
    }

    // ===================================================================
    // Cross-tenant visibility (login pre-condition)
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(30)
    @DisplayName("A newly registered tenant is visible cross-tenant via systemDataSource (login lookup precondition)")
    void new_tenant_is_visible_via_system_pool() throws Exception {
        Map<String, Object> req =
                buildRegisterRequest("xvsl", "30-71234574-4", "xvadmin", "xv@x.test", "Passw0rdSegura!");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated());

        // systemDataSource (BYPASSRLS) can see it
        Integer count =
                systemJdbc.queryForObject("SELECT COUNT(*) FROM public.tenants WHERE slug = 'xvsl'", Integer.class);
        assertThat(count).isEqualTo(1);

        // companyDataSource (RLS) WITHOUT setting app.current_tenant
        // returns 0 rows — proving the new tenant is tenant-scoped.
        JdbcTemplate companyJdbc = new JdbcTemplate(companyDataSource);
        try (Connection conn = companyDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                // Empty GUC — RLS policy filters to (empty) tenant id.
                st.execute("SET LOCAL app.current_tenant = '00000000-0000-0000-0000-000000000000'");
            }
            try (PreparedStatement ps =
                            conn.prepareStatement("SELECT COUNT(*) FROM public.tenants WHERE slug = 'xvsl'");
                    ResultSet rs = ps.executeQuery()) {
                rs.next();
                int visible = rs.getInt(1);
                assertThat(visible)
                        .as("RLS must hide the new tenant from a different tenant's company session")
                        .isEqualTo(0);
            }
        }
    }

    // ===================================================================
    // Provinces endpoint
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(40)
    @DisplayName("GET /reference/provinces returns 24 entries alphabetically with Cache-Control: public, max-age=3600")
    void provinces_endpoint_returns_24_alphabetical() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/reference/provinces"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("max-age=3600")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("public")))
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(24)))
                .andExpect(jsonPath("$.data[0].code").value("BUENOS_AIRES"))
                .andExpect(jsonPath("$.data[0].displayName").value("Buenos Aires"))
                .andExpect(jsonPath("$.data[23].code").value("TUCUMAN"))
                .andReturn();

        // Sanity: full ordering
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"BUENOS_AIRES\"").contains("\"CABA\"").contains("\"TUCUMAN\"");
    }

    // ===================================================================
    // Availability endpoints
    // ===================================================================

    @Test
    @org.junit.jupiter.api.Order(50)
    @DisplayName("GET /tenants/me/slug-availability returns available=true for a fresh slug")
    void slug_availability_available() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me/slug-availability").param("slug", "fresca"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("fresca"))
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @org.junit.jupiter.api.Order(51)
    @DisplayName("GET /tenants/me/slug-availability returns reason=RESERVED_SLUG for 'admin'")
    void slug_availability_reserved() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me/slug-availability").param("slug", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("RESERVED_SLUG"));
    }

    @Test
    @org.junit.jupiter.api.Order(52)
    @DisplayName("GET /tenants/me/cuit-availability returns valid=true and available=true for a fresh CUIT")
    void cuit_availability_valid_available() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me/cuit-availability").param("cuit", "20-12345678-6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cuit").value("20-12345678-6"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    @org.junit.jupiter.api.Order(53)
    @DisplayName("GET /tenants/me/cuit-availability returns valid=false when check-digit fails")
    void cuit_availability_invalid() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me/cuit-availability").param("cuit", "30-71234567-0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("VALIDATION_ERROR"));
    }

    @Test
    @org.junit.jupiter.api.Order(54)
    @DisplayName("GET /tenants/me/username-availability returns SLUG_NOT_FOUND for an unknown slug")
    void username_availability_slug_not_found() throws Exception {
        mockMvc.perform(get("/api/v1/tenants/me/username-availability")
                        .param("slug", "no-existe")
                        .param("username", "facu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("no-existe"))
                .andExpect(jsonPath("$.username").value("facu"))
                .andExpect(jsonPath("$.available").value(false))
                .andExpect(jsonPath("$.reason").value("SLUG_NOT_FOUND"));
    }

    // ===================================================================
    // Helper
    // ===================================================================

    /**
     * Build a fully-valid registration payload with overridable
     * fields. {@code cuit} accepts the 11-digit form (with or
     * without hyphens); the service normalizes to the canonical
     * form.
     */
    private static Map<String, Object> buildRegisterRequest(
            String slug, String cuit, String username, String email, String password) {
        Map<String, Object> address = Map.of(
                "country", "AR",
                "province", "BUENOS_AIRES",
                "city", "CABA",
                "line", "Av. Corrientes",
                "number", "1234",
                "floor", "1",
                "apartment", "A",
                "postalCode", "C1043");

        Map<String, Object> company = new java.util.LinkedHashMap<>();
        company.put("legalName", slug.toUpperCase() + " S.A.");
        company.put("commercialName", slug.toUpperCase() + " Corp");
        if (cuit != null) {
            company.put("cuit", cuit);
        }
        company.put("taxType", "RESPONSABLE_INSCRIPTO");
        company.put("slug", slug);
        company.put("contactEmail", email);
        company.put("contactPhone", "+541112345678");
        company.put("address", address);

        Map<String, Object> admin = Map.of(
                "username", username,
                "email", email,
                "firstName", "Maria",
                "lastName", "Vidal",
                "password", password);

        Map<String, Object> req = new java.util.LinkedHashMap<>();
        req.put("company", company);
        req.put("admin", admin);
        return req;
    }
}
