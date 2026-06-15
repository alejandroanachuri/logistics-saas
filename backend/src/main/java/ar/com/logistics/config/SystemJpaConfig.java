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
 * JPA + repository configuration for the {@code systemDataSource} —
 * the pool that runs under {@code app_admin} with {@code BYPASSRLS}.
 * Used by registration (writes new tenants + company_users), by
 * login (looks up users across tenants), and by refresh-token
 * validation (reads and rewrites tokens).
 *
 * <p>The entity packages are the same as the company side (Tenant,
 * CompanyUser, RefreshToken, Role) because the same tables are read
 * cross-tenant under {@code app_admin}. The package split is in the
 * repository layer, not the entity layer.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = {"ar.com.logistics.tenant.repository.admin", "ar.com.logistics.auth.repository.system"},
        entityManagerFactoryRef = "systemEntityManagerFactory",
        transactionManagerRef = "systemTransactionManager")
public class SystemJpaConfig {

    @Bean
    @ConfigurationProperties("app.jpa.system")
    public JpaProperties systemJpaProperties() {
        return new JpaProperties();
    }

    @Bean(name = "systemEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean systemEntityManagerFactory(
            @Qualifier("systemDataSource") DataSource dataSource,
            @Qualifier("systemJpaProperties") JpaProperties jpaProperties) {
        EntityManagerFactoryBuilder builder = createEntityManagerFactoryBuilder(jpaProperties);
        return builder.dataSource(dataSource)
                .packages("ar.com.logistics.tenant.domain", "ar.com.logistics.auth.domain")
                .persistenceUnit("system")
                .build();
    }

    @Bean(name = "systemTransactionManager")
    public PlatformTransactionManager systemTransactionManager(
            @Qualifier("systemEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
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
