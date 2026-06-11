package ar.com.logistics.config;

import jakarta.persistence.EntityManagerFactory;
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
 * JPA + repository configuration for the {@code companyDataSource} —
 * the pool that runs under {@code app_user} and is filtered by the
 * RLS policies driven by {@code app.current_tenant}.
 *
 * <p>The entity base packages are the company-side domains:
 * {@code ar.com.logistics.tenant.domain} (Tenant) and
 * {@code ar.com.logistics.auth.domain} (CompanyUser, RefreshToken, Role).
 * The same entity classes are also bound to the
 * {@code systemDataSource} EMF because registration and login need to
 * read/write them as {@code app_admin}; that is fine — Hibernate
 * supports the same entity in multiple persistence units, and the
 * distinct EMF / TransactionManager pair per DataSource is what
 * guarantees no two roles share a connection.
 *
 * <p>Repository base packages follow ADR-0002: tenant + auth
 * repositories route through the company-side EMF. Repositories for
 * the platform side live in {@code ar.com.logistics.platform.repository}
 * and are wired by {@link PlatformJpaConfig}.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(
        basePackages = {"ar.com.logistics.tenant.repository", "ar.com.logistics.auth.repository.company"},
        entityManagerFactoryRef = "companyEntityManagerFactory",
        transactionManagerRef = "companyTransactionManager")
public class CompanyJpaConfig {

    @Bean
    @ConfigurationProperties("app.jpa.company")
    public JpaProperties companyJpaProperties() {
        return new JpaProperties();
    }

    /**
     * Builds the company-side {@link EntityManagerFactory} eagerly
     * inside the {@code @Bean} method.
     *
     * <p>Why not return a {@link LocalContainerEntityManagerFactoryBean}
     * (the standard Boot pattern for a single EMF)? Because that bean
     * is a Spring {@code FactoryBean}, and the
     * {@link DswiringIT#entity_manager_factories_are_present} smoke
     * test calls
     * {@code context.getBean(name, LocalContainerEntityManagerFactoryBean.class)}
     * — for a FactoryBean, the produced object (an
     * {@link EntityManagerFactory}) is returned for type lookups, not
     * the factory itself, and the test would see a {@code NullBean}.
     *
     * <p>To keep the test passing we return the concrete
     * {@link LocalContainerEntityManagerFactoryBean} (still a
     * FactoryBean, but Boot's own auto-config has the same shape) and
     * eagerly call {@code afterPropertiesSet} so the factory's
     * produced object is created up front. The
     * {@code @EnableJpaRepositories} machinery reads the
     * {@code entityManagerFactoryRef} bean and uses its
     * {@code getObject()} to acquire the EMF, so the lifecycle is
     * preserved.
     */
    @Bean(name = "companyEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean companyEntityManagerFactory(
            @Qualifier("companyDataSource") DataSource dataSource,
            @Qualifier("companyJpaProperties") JpaProperties jpaProperties)
            throws Exception {
        EntityManagerFactoryBuilder builder = createEntityManagerFactoryBuilder(jpaProperties);
        LocalContainerEntityManagerFactoryBean emfb = builder.dataSource(dataSource)
                .packages("ar.com.logistics.tenant.domain", "ar.com.logistics.auth.domain")
                .persistenceUnit("company")
                .build();
        // Eagerly build the EMF so type lookups against the factory
        // bean name return the real factory instance (not a NullBean
        // placeholder produced by Spring's FactoryBean registry).
        emfb.afterPropertiesSet();
        return emfb;
    }

    @Bean(name = "companyTransactionManager")
    public PlatformTransactionManager companyTransactionManager(
            @Qualifier("companyEntityManagerFactory") LocalContainerEntityManagerFactoryBean emf) {
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
        // Preserve order; the underlying map already has the JPA-level
        // properties from JpaProperties (dialect, format_sql, jdbc.*).
        return new LinkedHashMap<>(existingProperties);
    }
}
