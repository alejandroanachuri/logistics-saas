package ar.com.logistics.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import javax.sql.DataSource;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RlsAspect — the contract surface for the company-side
 * RLS-aware data path. The {@code @Around} advice fires on every
 * method in {@code ar.com.logistics.tenant.repository..*}; the actual
 * {@code SET LOCAL app.current_tenant = '<uuid>'} emission is delegated
 * to {@link RlsConnectionCustomizer}, which uses a
 * {@code TransactionSynchronization} so the GUC lands on the JDBC
 * connection that Hibernate will actually use.
 *
 * <p>Design references:
 * <ul>
 *   <li>PRD line 491: "SET LOCAL app.current_tenant per request"</li>
 *   <li>spec/multi-tenant-data-isolation.md, scenario
 *       "SET LOCAL applied per request"</li>
 *   <li>design.md §1.3 (TenantContext Flow)</li>
 * </ul>
 *
 * <p>Why not a Hibernate {@code StatementInspector}? Hibernate 7's
 * inspector has no access to the underlying JDBC connection — it only
 * sees the SQL string. We need to write the GUC on the actual
 * connection before any SQL hits the database. A
 * {@code TransactionSynchronization} is the cleanest available hook
 * with full connection access.
 */
@Aspect
@Component
public class RlsAspect {

    private static final Logger LOG = LoggerFactory.getLogger(RlsAspect.class);

    private final boolean rlsEnabled;

    private final RlsConnectionCustomizer rlsConnectionCustomizer;

    /**
     * Package-private reference to the company-side DataSource, kept
     * here so {@link RlsConnectionCustomizer} can call
     * {@link org.springframework.jdbc.datasource.DataSourceUtils#getConnection(DataSource)}
     * on the right pool without a circular constructor.
     */
    private static volatile DataSource COMPANY_DATA_SOURCE;

    public RlsAspect(
            @Value("${app.rls.enabled:true}") boolean rlsEnabled,
            @Qualifier("companyDataSource") DataSource companyDataSource,
            RlsConnectionCustomizer rlsConnectionCustomizer) {
        this.rlsEnabled = rlsEnabled;
        this.rlsConnectionCustomizer = rlsConnectionCustomizer;
        COMPANY_DATA_SOURCE = companyDataSource;
        if (!rlsEnabled) {
            LOG.warn("RLS disabled via app.rls.enabled=false — companyDataSource "
                    + "queries will return rows from all tenants");
        }
    }

    /** Read-only view of the company-side DataSource. */
    static DataSource companyDataSource() {
        return COMPANY_DATA_SOURCE;
    }

    /**
     * Fires on the company-side repository entry point. The pointcut
     * matches the {@code tenant.repository} package only (not the
     * whole Spring Data Repository hierarchy) so it does not cross
     * into the platform or auth-domain services that route via
     * systemDataSource.
     */
    @Around("execution(* ar.com.logistics.tenant.repository..*(..))")
    public Object emitCurrentTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!rlsEnabled) {
            return joinPoint.proceed();
        }
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId == null) {
            // No tenant in the request thread — refuse rather than
            // accidentally run with an unfiltered pool. Failing loud
            // is safer than silently bypassing RLS.
            throw new IllegalStateException(
                    "companyDataSource accessed without a TenantContext. Did the authentication filter run?");
        }
        // Register a tx synchronization that will emit SET LOCAL
        // before commit. The aspect then proceeds; the synchronization
        // fires after the tx is bound to a connection and before any
        // user SQL runs.
        rlsConnectionCustomizer.register();
        return TenantAwareConnectionScope.run(tenantId, joinPoint::proceed);
    }

    /**
     * Direct entry point for callers that already hold a JDBC
     * {@link Connection} (e.g. raw JdbcTemplate usage). Emits
     * {@code SET LOCAL} on the connection. The connection MUST be
     * inside an active transaction (otherwise Postgres rejects the
     * statement with SQLSTATE 25001).
     */
    public static void setCurrentTenantOnConnection(Connection connection, UUID tenantId) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("SET LOCAL app.current_tenant = '" + tenantId + "'");
        }
    }
}
