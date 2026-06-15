package ar.com.logistics.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Platform-side DataSource. Connects as {@code app_platform} which
 * is governed by the cross-tenant RLS policies (PRD line 469). Every
 * endpoint under {@code /api/v1/platform/**} routes through this
 * pool in PR8.
 *
 * <p>Bean name {@code platformDataSource}.
 *
 * <p>Configuration prefix: {@code app.datasource.platform}.
 */
@Configuration(proxyBeanMethods = false)
public class PlatformDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.platform")
    public DataSourceProperties platformDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "platformDataSource", destroyMethod = "close")
    @ConfigurationProperties("app.datasource.platform.configuration")
    public HikariDataSource platformDataSource(DataSourceProperties platformDataSourceProperties) {
        return platformDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
