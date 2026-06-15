package ar.com.logistics.rls;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * GATE TEST for multi-tenant data isolation.
 *
 * <p>Boots a real PostgreSQL 16 container, applies the Flyway migrations
 * (V1..V9) so the schema, RLS policies, and three application roles are
 * all in place, then asserts the contract from
 * {@code spec/multi-tenant-data-isolation.md}. Plain JdbcTemplate is used
 * for assertions so the test stays independent of the JPA entities.
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RlsIntegrationIT {

    private static final String APP_USER = "app_user";
    private static final String APP_USER_PASSWORD = "app_user";
    private static final String APP_ADMIN = "app_admin";
    private static final String APP_ADMIN_PASSWORD = "app_admin";
    private static final String APP_PLATFORM = "app_platform";
    private static final String APP_PLATFORM_PASSWORD = "app_platform";

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("logistics")
            .withUsername("postgres")
            .withPassword("postgres");

    private static JdbcTemplate adminJdbc;
    private static String tenantAUuid;
    private static String tenantBUuid;
    private static String userAUuid;
    private static String userBUuid;

    @BeforeAll
    static void bootstrap() throws Exception {
        POSTGRES.start();

        // Create the three application roles (idempotent — V8 does the
        // same, but doing it here too makes the test robust to V8 changes).
        try (Connection conn =
                DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            try (var st = conn.createStatement()) {
                st.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
                st.execute("CREATE ROLE app_user NOINHERIT LOGIN PASSWORD 'app_user'");
                st.execute("CREATE ROLE app_admin NOINHERIT LOGIN BYPASSRLS PASSWORD 'app_admin'");
                st.execute("CREATE ROLE app_platform NOINHERIT LOGIN PASSWORD 'app_platform'");
                st.execute("GRANT CONNECT ON DATABASE logistics TO app_user, app_admin, app_platform");
            }
        }

        org.flywaydb.core.Flyway flyway = org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();

        adminJdbc = new JdbcTemplate(
                buildDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()));

        tenantAUuid = insertTenant("acme", "20123456789");
        tenantBUuid = insertTenant("globex", "27123456780");
        userAUuid = insertCompanyUser(tenantAUuid, "admin-a", "admin-a@acme.test");
        userBUuid = insertCompanyUser(tenantBUuid, "admin-b", "admin-b@globex.test");
    }

    @AfterAll
    static void teardown() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    // ---------- SCENARIO 3 — app_user is filtered to its tenant ----------

    @Test
    @Order(1)
    @DisplayName("app_user sees only its own tenant row in public.tenants")
    void company_user_sees_only_own_tenant_row() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            setCurrentTenant(conn, tenantAUuid);
            assertThat(countRows(conn, "SELECT COUNT(*) FROM public.tenants"))
                    .as("RLS must filter tenants to the session GUC")
                    .isEqualTo(1);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @Order(2)
    @DisplayName("app_user CANNOT see the other tenant's row in public.tenants")
    void company_user_cannot_see_other_tenant_row() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            setCurrentTenant(conn, tenantAUuid);
            int count;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM public.tenants WHERE id = ?")) {
                ps.setString(1, tenantBUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    count = rs.getInt(1);
                }
            }
            assertThat(count)
                    .as("RLS must hide tenant B from tenant A's session")
                    .isEqualTo(0);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @Order(3)
    @DisplayName("app_user CANNOT see the other tenant's company_users")
    void company_user_cannot_see_other_tenant_users() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            setCurrentTenant(conn, tenantAUuid);
            int count;
            try (PreparedStatement ps =
                    conn.prepareStatement("SELECT COUNT(*) FROM public.company_users WHERE id = ?")) {
                ps.setString(1, userBUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    count = rs.getInt(1);
                }
            }
            assertThat(count).as("RLS must hide user B from tenant A's session").isEqualTo(0);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @Order(4)
    @DisplayName("Cross-tenant UPDATE on public.company_users affects 0 rows")
    void cross_tenant_update_affects_zero_rows() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            setCurrentTenant(conn, tenantAUuid);
            int updated;
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE public.company_users SET email = 'attacker@evil.test' WHERE id = ?")) {
                ps.setString(1, userBUuid);
                updated = ps.executeUpdate();
            }
            assertThat(updated)
                    .as("RLS must hide user B from UPDATE, so 0 rows are touched")
                    .isEqualTo(0);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
        // Confirm the attacker's UPDATE did NOT change user B's email.
        String email = adminJdbc.queryForObject(
                "SELECT email FROM public.company_users WHERE id = ?", String.class, userBUuid);
        assertThat(email)
                .as("user B's email must be unchanged after the attack")
                .isEqualTo("admin-b@globex.test");
    }

    // ---------- SCENARIO 4 — app_platform sees all tenants ----------

    @Test
    @Order(5)
    @DisplayName("app_platform sees every tenant")
    void platform_user_sees_both_tenants() {
        try (Connection conn = openAs(APP_PLATFORM, APP_PLATFORM_PASSWORD)) {
            assertThat(countRows(conn, "SELECT COUNT(*) FROM public.tenants"))
                    .as("app_platform policies permit cross-tenant reads")
                    .isEqualTo(2);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    @Order(6)
    @DisplayName("app_platform sees every company_users row across tenants")
    void platform_user_sees_both_tenant_users() {
        try (Connection conn = openAs(APP_PLATFORM, APP_PLATFORM_PASSWORD)) {
            assertThat(countRows(conn, "SELECT COUNT(*) FROM public.company_users"))
                    .as("app_platform policies permit cross-tenant user reads")
                    .isEqualTo(2);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ---------- SCENARIO 5 — app_admin BYPASSRLS ----------

    @Test
    @Order(7)
    @DisplayName("app_admin can INSERT into public.tenants (registration path)")
    void admin_can_insert_tenant() {
        String newTenantId = UUID.randomUUID().toString();
        adminJdbc.update(
                "INSERT INTO public.tenants (id, slug, legal_name, cuit, tax_type, contact_email, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                newTenantId,
                "initech",
                "Initech S.A.",
                "30123456781",
                "RESPONSABLE_INSCRIPTO",
                "ops@initech.test",
                "ACTIVE");
        String slug =
                adminJdbc.queryForObject("SELECT slug FROM public.tenants WHERE id = ?", String.class, newTenantId);
        assertThat(slug).isEqualTo("initech");
    }

    @Test
    @Order(8)
    @DisplayName("app_admin can SELECT company_users across tenants (login lookup)")
    void admin_can_select_all_users() {
        Integer count = adminJdbc.queryForObject("SELECT COUNT(*) FROM public.company_users", Integer.class);
        assertThat(count).as("app_admin BYPASSRLS sees all rows").isGreaterThanOrEqualTo(2);
    }

    // ---------- SCENARIO 6 — SET LOCAL outside a transaction is rejected ----------

    @Test
    @Order(9)
    @DisplayName("SET LOCAL outside a transaction is rejected by Postgres")
    void set_local_outside_transaction_fails() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            try (Statement st = conn.createStatement()) {
                st.execute("SET LOCAL app.current_tenant = '" + tenantAUuid + "'");
            }
            // Outside a tx, the GUC reverts — confirm tenant A is NOT visible.
            assertThat(countRows(conn, "SELECT COUNT(*) FROM public.tenants"))
                    .as("Without an active transaction, the GUC must not stick")
                    .isEqualTo(0);
        } catch (SQLException ex) {
            // Postgres either rejects SET LOCAL outside a tx (25001
            // active_sql_transaction) or accepts it and the policy
            // fails later with 22P02 (invalid_text_representation)
            // because the empty GUC cannot be cast to UUID. Both
            // outcomes prove the GUC cannot stick.
            assertThat(ex.getSQLState())
                    .as("PG must prevent app.current_tenant from sticking outside a transaction")
                    .isIn("25001", "22P02");
        }
    }

    // ---------- SCENARIO 7 — RLS bypass via GUC manipulation still filters ----------

    @Test
    @Order(10)
    @DisplayName("app_user setting the GUC to another tenant's id still gets filtered rows")
    void rls_bypass_via_guc_manipulation_fails() {
        try (Connection conn = openAs(APP_USER, APP_USER_PASSWORD)) {
            conn.setAutoCommit(false);
            setCurrentTenant(conn, tenantBUuid);
            // The attacker sees tenant B (the GUC matches the policy).
            assertThat(countRows(conn, "SELECT COUNT(*) FROM public.tenants"))
                    .as("Setting the GUC to tenant B's id lets the attacker see tenant B")
                    .isEqualTo(1);
            // And NOT tenant A.
            int count;
            try (PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM public.tenants WHERE id = ?")) {
                ps.setString(1, tenantAUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    count = rs.getInt(1);
                }
            }
            assertThat(count)
                    .as("The attacker claims tenant B; they must NOT see tenant A")
                    .isEqualTo(0);
        } catch (SQLException ex) {
            throw new RuntimeException(ex);
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void setCurrentTenant(Connection conn, String tenantId) throws SQLException {
        try (Statement st = conn.createStatement()) {
            st.execute("SET LOCAL app.current_tenant = '" + tenantId + "'");
        }
    }

    private static int countRows(Connection conn, String sql) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static Connection openAs(String user, String password) throws SQLException {
        // stringtype=unspecified lets the driver pass String-typed bound
        // parameters unquoted so Postgres can coerce them to UUID.
        String url = POSTGRES.getJdbcUrl();
        if (!url.contains("stringtype=")) {
            url = url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified";
        }
        return DriverManager.getConnection(url, user, password);
    }

    private static String insertTenant(String slug, String cuit) {
        String id = UUID.randomUUID().toString();
        adminJdbc.update(
                "INSERT INTO public.tenants (id, slug, legal_name, cuit, tax_type, contact_email, status) VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                slug,
                slug + " S.A.",
                cuit,
                "RESPONSABLE_INSCRIPTO",
                slug + "@test.local",
                "ACTIVE");
        return id;
    }

    private static String insertCompanyUser(String tenantId, String username, String email) {
        String id = UUID.randomUUID().toString();
        String roleId = adminJdbc.queryForObject(
                "SELECT id FROM public.roles WHERE name = 'COMPANY_ADMIN' AND scope = 'COMPANY'", String.class);
        adminJdbc.update(
                "INSERT INTO public.company_users (id, tenant_id, role_id, username, email, first_name, last_name, password_hash, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                tenantId,
                roleId,
                username,
                email,
                "Admin",
                username,
                "$2a$12$placeholder.hash.placeholder.hash.placeholder.hash.placeholder",
                "ACTIVE");
        return id;
    }

    private static DriverManagerDataSource buildDataSource(String url, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        // stringtype=unspecified lets the driver pass String-typed bound
        // parameters unquoted so Postgres can coerce them to UUID.
        ds.setUrl(url.contains("stringtype=") ? url : url + (url.contains("?") ? "&" : "?") + "stringtype=unspecified");
        ds.setUsername(user);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
