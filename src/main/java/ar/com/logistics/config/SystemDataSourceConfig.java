package ar.com.logistics.config;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/**
 * System-side DataSource. Connects as {@code app_admin} which has
 * {@code BYPASSRLS}, so registration, login lookup, slug/CUIT
 * availability checks, and refresh-token validation can read and
 * write across tenants.
 *
 * <p>Bean name {@code systemDataSource} is the autowire qualifier used
 * by the registration service (PR3) and the audit logger (PR2).
 *
 * <p>Configuration prefix: {@code app.datasource.system}.
 *
 * <p>Spring Boot 4 removed Flyway auto-configuration entirely.
 * With three DataSources present (company + platform + system)
 * the auto-configuration never wires a Flyway instance, so the
 * V1..V10 migrations never run and every {@code tenants/*}
 * endpoint returns 500 with {@code relation "tenants" does not
 * exist}. We bind Flyway explicitly to the system DataSource
 * here and invoke {@code migrate()} from a high-priority
 * CommandLineRunner so migrations run on application startup
 * before the HTTP server accepts traffic.
 *
 * <p>The {@code app_admin} role (used by the system DataSource)
 * has BYPASSRLS, which is required for the V8 RLS policy
 * grants and the V10 audit column DDL.
 */
@Configuration(proxyBeanMethods = false)
public class SystemDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.system")
    public DataSourceProperties systemDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "systemDataSource", destroyMethod = "close")
    @ConfigurationProperties("app.datasource.system.configuration")
    public HikariDataSource systemDataSource(DataSourceProperties systemDataSourceProperties) {
        return systemDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    /**
     * Explicit Flyway configuration bound to the
     * {@code systemDataSource}. With Spring Boot 4's removal of
     * Flyway auto-configuration, the application is responsible
     * for instantiating the {@link Flyway} bean and invoking
     * {@code migrate()} explicitly. The {@link CommandLineRunner}
     * below does the invocation at the highest priority (lowest
     * order number) so migrations complete before any HTTP
     * endpoint is exercised.
     */
    @Bean
    public Flyway systemFlyway(@Qualifier("systemDataSource") DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .validateOnMigrate(false)
                .load();
    }

    /**
     * Run Flyway migrations at application startup. {@code @Order(0)}
     * ensures this runs before any other {@link CommandLineRunner}
     * (the default order is {@link Integer#MAX_VALUE}). Without
     * this, the V1..V10 migrations never execute and the
     * {@code tenants}, {@code company_users}, etc. tables do not
     * exist in the database.
     */
    @Bean
    @Order(0)
    public CommandLineRunner flywayMigrator(Flyway systemFlyway) {
        return args -> {
            var result = systemFlyway.migrate();
            // The migrate() call is silent; emit a single INFO line
            // so the operator can see what happened in the boot log.
            org.slf4j.LoggerFactory.getLogger(SystemDataSourceConfig.class)
                    .info("Flyway migrate() complete: {} migrations applied, success={}",
                            result.migrationsExecuted, result.success);
        };
    }
}
