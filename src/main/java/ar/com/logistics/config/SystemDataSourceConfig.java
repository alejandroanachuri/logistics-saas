package ar.com.logistics.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
