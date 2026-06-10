package ar.com.logistics.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Company-side DataSource. Connects as {@code app_user} so every query
 * runs under the RLS policies that filter rows by {@code app.current_tenant}.
 *
 * <p>Bean name {@code companyDataSource} is referenced by the
 * {@code @EnableJpaRepositories} on the JPA config and by the
 * DswiringIT smoke test.
 *
 * <p>Configuration prefix: {@code app.datasource.company}. Pool sizing
 * lives under {@code app.datasource.company.configuration} (Hikari).
 *
 * @see <a href="https://docs.spring.io/spring-boot/4.0-SNAPSHOT/how-to/data-access.html">Spring Boot 4 — Advanced DataSource Configuration</a>
 */
@Configuration(proxyBeanMethods = false)
public class CompanyDataSourceConfig {

    @Bean
    @ConfigurationProperties("app.datasource.company")
    public DataSourceProperties companyDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "companyDataSource", destroyMethod = "close")
    @ConfigurationProperties("app.datasource.company.configuration")
    public HikariDataSource companyDataSource(DataSourceProperties companyDataSourceProperties) {
        // Build the Hikari pool from the DataSourceProperties bean so
        // URL translation (jdbc-url vs url) is handled by Spring Boot.
        return companyDataSourceProperties
                .initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }
}
