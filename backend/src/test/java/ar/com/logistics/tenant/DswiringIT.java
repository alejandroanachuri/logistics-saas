package ar.com.logistics.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PR1d — Spring-side wiring of the three-DataSource architecture.
 *
 * <p>Complements {@link ar.com.logistics.rls.RlsIntegrationIT} (the DB-level
 * GATE test). Where RlsIntegrationIT proves RLS works against the raw schema
 * via JDBC, DswiringIT proves the Spring context wires the three
 * {@code companyDataSource} / {@code systemDataSource} / {@code platformDataSource}
 * beans with their own {@link LocalContainerEntityManagerFactoryBean} and
 * {@link PlatformTransactionManager} each, that the Hikari pool sizes match
 * the spec, and that a real cross-tenant SELECT through the
 * company-scoped JPA EMF is filtered while the same SELECT through the
 * system-scoped EMF sees both tenants.
 */
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DswiringIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("logistics")
            .withUsername("postgres")
            .withPassword("postgres");

    /**
     * Point all three application DataSources at the Testcontainers
     * Postgres. The credentials match the roles created in
     * {@link #bootstrapSchema()}. The Hikari pool size in
     * application.yml is 5; the tests do not change it.
     */
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
        // Flyway runs against the test container too.
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    @Qualifier("companyDataSource")
    private DataSource companyDataSource;

    @Autowired
    @Qualifier("systemDataSource")
    private DataSource systemDataSource;

    @Autowired
    @Qualifier("platformDataSource")
    private DataSource platformDataSource;

    private static String tenantAUuid;
    private static String tenantBUuid;

    @BeforeAll
    static void bootstrapSchema() throws Exception {
        POSTGRES.start();
        // Mirror the same pre-Flyway role setup as RlsIntegrationIT.
        try (Connection conn = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = conn.createStatement()) {
            st.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
            st.execute("CREATE ROLE app_user NOINHERIT LOGIN PASSWORD 'app_user'");
            st.execute("CREATE ROLE app_admin NOINHERIT LOGIN BYPASSRLS PASSWORD 'app_admin'");
            st.execute("CREATE ROLE app_platform NOINHERIT LOGIN PASSWORD 'app_platform'");
            st.execute("GRANT CONNECT ON DATABASE logistics TO app_user, app_admin, app_platform");
        }
        org.flywaydb.core.Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
        // Seed two tenants that will be used by the cross-tenant assertions.
        tenantAUuid = UUID.randomUUID().toString();
        tenantBUuid = UUID.randomUUID().toString();
        try (Connection conn = DriverManager.getConnection(
                        POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                Statement st = conn.createStatement()) {
            st.execute("INSERT INTO public.tenants (id, slug, legal_name, cuit, tax_type, contact_email, status) "
                    + "VALUES ('" + tenantAUuid + "', 'dswire-a', 'Dswire A S.A.', '30111111111', "
                    + "'RESPONSABLE_INSCRIPTO', 'a@dswire.test', 'ACTIVE')");
            st.execute("INSERT INTO public.tenants (id, slug, legal_name, cuit, tax_type, contact_email, status) "
                    + "VALUES ('" + tenantBUuid + "', 'dswire-b', 'Dswire B S.A.', '30222222222', "
                    + "'RESPONSABLE_INSCRIPTO', 'b@dswire.test', 'ACTIVE')");
        }
    }

    @AfterAll
    static void teardown() {
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    // ---------- Bean presence ----------

    @Test
    @Order(1)
    @DisplayName("All three EntityManagerFactory beans are present in the Spring context")
    void entity_manager_factories_are_present() {
        // LocalContainerEntityManagerFactoryBean is a Spring FactoryBean — the
        // bean registry stores the produced EntityManagerFactory under the
        // factory's name, not the factory itself. Look the EMF up by its
        // produced type to get the real instance.
        assertThat(context.getBean("companyEntityManagerFactory", EntityManagerFactory.class))
                .as("companyEntityManagerFactory must be wired")
                .isNotNull();
        assertThat(context.getBean("systemEntityManagerFactory", EntityManagerFactory.class))
                .as("systemEntityManagerFactory must be wired")
                .isNotNull();
        assertThat(context.getBean("platformEntityManagerFactory", EntityManagerFactory.class))
                .as("platformEntityManagerFactory must be wired")
                .isNotNull();
        // Sanity check: the FactoryBean itself is also accessible.
        assertThat(context.getBean("&companyEntityManagerFactory", LocalContainerEntityManagerFactoryBean.class))
                .as("&companyEntityManagerFactory resolves to the factory bean itself")
                .isNotNull();
    }

    @Test
    @Order(2)
    @DisplayName("All three TransactionManager beans are present in the Spring context")
    void transaction_managers_are_present() {
        assertThat(context.getBean("companyTransactionManager", PlatformTransactionManager.class))
                .as("companyTransactionManager must be wired")
                .isNotNull();
        assertThat(context.getBean("systemTransactionManager", PlatformTransactionManager.class))
                .as("systemTransactionManager must be wired")
                .isNotNull();
        assertThat(context.getBean("platformTransactionManager", PlatformTransactionManager.class))
                .as("platformTransactionManager must be wired")
                .isNotNull();
    }

    // ---------- Pool sizing ----------

    @Test
    @Order(3)
    @DisplayName("Each DataSource is a HikariDataSource with maximum-pool-size=5")
    void hikari_pool_size_is_five() {
        for (Map.Entry<String, DataSource> entry : Map.of(
                        "companyDataSource", companyDataSource,
                        "systemDataSource", systemDataSource,
                        "platformDataSource", platformDataSource)
                .entrySet()) {
            assertThat(entry.getValue())
                    .as("%s must be a HikariDataSource", entry.getKey())
                    .isInstanceOf(HikariDataSource.class);
            HikariDataSource hikari = (HikariDataSource) entry.getValue();
            assertThat(hikari.getMaximumPoolSize())
                    .as("%s maximum-pool-size", entry.getKey())
                    .isEqualTo(5);
        }
    }

    // ---------- End-to-end: company DS is RLS-filtered, system DS bypasses RLS ----------

    @Test
    @Order(4)
    @DisplayName("companyDataSource applies RLS: SET LOCAL filters tenants to the GUC")
    void company_data_source_is_rls_filtered() throws Exception {
        try (Connection conn = companyDataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                st.execute("SET LOCAL app.current_tenant = '" + tenantAUuid + "'");
            }
            int total;
            try (Statement st = conn.createStatement();
                    var rs = st.executeQuery("SELECT COUNT(*) FROM public.tenants")) {
                rs.next();
                total = rs.getInt(1);
            }
            assertThat(total)
                    .as("RLS must filter tenants to the GUC for companyDataSource")
                    .isEqualTo(1);
        }
    }

    @Test
    @Order(5)
    @DisplayName("systemDataSource bypasses RLS: it sees every tenant regardless of the GUC")
    void system_data_source_bypasses_rls() throws Exception {
        try (Connection conn = systemDataSource.getConnection();
                Statement st = conn.createStatement();
                var rs = st.executeQuery("SELECT COUNT(*) FROM public.tenants")) {
            rs.next();
            int total = rs.getInt(1);
            assertThat(total)
                    .as("app_admin has BYPASSRLS, so systemDataSource must see every tenant")
                    .isGreaterThanOrEqualTo(2);
        }
    }
}
