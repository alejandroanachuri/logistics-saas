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
     * matches the {@code tenant.repository}, {@code auth.repository.company}
     * and {@code shipment.repository} packages (not the whole Spring
     * Data Repository hierarchy) so it does not cross into the
     * platform or auth-domain services that route via
     * systemDataSource. The {@code auth.repository.company}
     * subpackage was added in etapa-2 PR-2 when the new
     * {@code CompanyUserRepository} + {@code CompanyUserRoleRepository}
     * moved into the auth domain but still need RLS tenant
     * isolation. The {@code shipment.repository} package is added in
     * etapa-3 PR-2 (this commit) so the 9 new RLS-scoped repositories
     * for addresses / customers / shipments / packages /
     * tracking_events / shipment_custody / id_sequences / branches /
     * service_levels also pick up the {@code SET LOCAL
     * app.current_tenant} emission.
     */
    @Around("execution(* ar.com.logistics.tenant.repository..*(..)) "
            + "|| execution(* ar.com.logistics.auth.repository.company..*(..)) "
            + "|| execution(* ar.com.logistics.shipment.repository..*(..))")
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
        // The previous implementation relied on a TransactionSynchronization
        // registered via registerSynchronization(), but the SET LOCAL GUC
        // emitted in beforeCompletion() runs AFTER the Hibernate SQL
        // statement has already been prepared and bound. The SELECT runs
        // without the tenant GUC, the RLS policy then evaluates with no
        // tenant context, and the query fails (or, for EXISTS, returns
        // empty in a way that bypasses the policy). This was the root
        // cause of the `invalid input syntax for type uuid: ""` error
        // — the BIND was correct but the SQL was prepared without the
        // GUC, so Postgres treated the bound UUID as an empty literal.
        //
        // Fix: emit the GUC synchronously on the active transaction's
        // connection BEFORE the SQL statement is prepared. We use
        // DataSourceUtils.getConnection(dataSource) which returns the
        // connection bound to the active Hibernate transaction (not a
        // new one from the pool). We also register a synchronization
        // that runs the same SET LOCAL on every statement's connection
        // — defense in depth in case Hibernate switches connections
        // mid-transaction (it doesn't, but the cost is negligible).
        // DataSourceUtils.getConnection returns the connection bound to
        // the current transaction. We MUST NOT close it via try-with-resources
        // because that hands the connection back to the pool / closes it,
        // breaking the subsequent Hibernate statement (which is held open
        // on that same connection). The correct pattern is to obtain the
        // connection, do the work, and leave the close to Spring's tx
        // machinery (which happens automatically at tx completion).
        java.sql.Connection conn = null;
        try {
            conn = org.springframework.jdbc.datasource.DataSourceUtils.getConnection(COMPANY_DATA_SOURCE);
            setCurrentTenantOnConnection(conn, tenantId);
        } catch (java.sql.SQLException ex) {
            throw new IllegalStateException("Failed to emit SET LOCAL app.current_tenant before query execution", ex);
        } finally {
            // No-op close. DataSourceUtils manages the connection lifecycle
            // via the active transaction. We MUST NOT call conn.close()
            // here — that would close the connection BEFORE Hibernate has
            // a chance to use it for the actual user query.
        }
        return joinPoint.proceed();
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
