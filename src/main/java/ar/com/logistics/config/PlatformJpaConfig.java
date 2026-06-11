package ar.com.logistics.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jpa.autoconfigure.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.JpaVendorAdapter;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JPA + repository configuration for the {@code platformDataSource} —
 * the pool that runs under {@code app_platform} and is governed by
 * the cross-tenant RLS policies.
 *
 * <p>Entity base package is {@code ar.com.logistics.platform.domain}
 * (PlatformUser) plus, in a later PR, the read-only platform-side
 * projections of Tenant and CompanyUser. Repositories live under
 * {@code ar.com.logistics.platform.repository}.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = "ar.com.logistics.platform.repository",
        entityManagerFactoryRef = "platformEntityManagerFactory",
        transactionManagerRef = "platformTransactionManager")
public class PlatformJpaConfig {

    @Bean
    @ConfigurationProperties("app.jpa.platform")
    public JpaProperties platformJpaProperties() {
        return new JpaProperties();
    }

    @Bean(name = "platformEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean platformEntityManagerFactory(
            @Qualifier("platformDataSource") DataSource dataSource,
            @Qualifier("platformJpaProperties") JpaProperties jpaProperties) {
        EntityManagerFactoryBuilder builder = createEntityManagerFactoryBuilder(jpaProperties);
        // TenantAdminRepository (cross-tenant lookup) operates on
        // ar.com.logistics.tenant.domain.Tenant; the cross-tenant
        // RLS policies on the platform pool let us read all rows.
        // Same entity in multiple persistence units is OK per ADR-0002.
        return builder.dataSource(dataSource)
                .packages(
                        "ar.com.logistics.platform.domain",
                        "ar.com.logistics.tenant.domain",
                        "ar.com.logistics.auth.domain")
                .persistenceUnit("platform")
                .build();
    }

    @Bean(name = "platformTransactionManager")
    public PlatformTransactionManager platformTransactionManager(
            @Qualifier("platformEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
        return new JpaTransactionManager(emf.getObject());
    }

    private static EntityManagerFactoryBuilder createEntityManagerFactoryBuilder(JpaProperties jpaProperties) {
        JpaVendorAdapter jpaVendorAdapter = createJpaVendorAdapter(jpaProperties);
        Function<DataSource, Map<String, ?>> jpaPropertiesFactory =
                (dataSource) -> createJpaProperties(dataSource, jpaProperties.getProperties());
        return new EntityManagerFactoryBuilder(jpaVendorAdapter, jpaPropertiesFactory, null);
    }

    private static JpaVendorAdapter createJpaVendorAdapter(JpaProperties jpaProperties) {
        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        adapter.setGenerateDdl(false);
        return adapter;
    }

    private static Map<String, ?> createJpaProperties(DataSource dataSource, Map<String, ?> existingProperties) {
        return new LinkedHashMap<>(existingProperties);
    }
}
