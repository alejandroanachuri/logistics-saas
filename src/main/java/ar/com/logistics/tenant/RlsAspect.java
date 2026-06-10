package ar.com.logistics.tenant;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * RlsAspect — emits {@code SET LOCAL app.current_tenant = '<uuid>'}
 * on every transaction that runs through the {@code companyDataSource}
 * (per ADR-0002). The actual GUC is written to the JDBC connection
 * by intercepting the {@code @Transactional} invocation on methods in
 * the {@code ar.com.logistics.tenant.repository} package (and later
 * {@code ar.com.logistics.auth.repository}).
 *
 * <p>Design references:
 * <ul>
 *   <li>PRD line 491: "SET LOCAL app.current_tenant per request"</li>
 *   <li>spec/multi-tenant-data-isolation.md, scenario
 *       "SET LOCAL applied per request"</li>
 *   <li>design.md §1.3 (TenantContext Flow)</li>
 * </ul>
 *
 * <p>Why an aspect instead of a {@code TransactionSynchronization}?
 * The aspect runs INSIDE the Spring AOP chain right before the
 * repository call, so the GUC is in place before Hibernate issues any
 * statement. SET LOCAL is transaction-scoped, so it auto-reverts at
 * commit/rollback — exactly the spec contract.
 *
 * <p>Why not for system / platform?
 * <ul>
 *   <li>{@code systemDataSource} connects as {@code app_admin} with
 *       {@code BYPASSRLS}; RLS is off, so the GUC has no effect.</li>
 *   <li>{@code platformDataSource} is for {@code app_platform} users
 *       which have no tenant concept (cross-tenant operations).</li>
 * </ul>
 */
@Aspect
@Component
public class RlsAspect {

    private static final Logger LOG = LoggerFactory.getLogger(RlsAspect.class);

    private final boolean rlsEnabled;

    public RlsAspect(@Value("${app.rls.enabled:true}") boolean rlsEnabled) {
        this.rlsEnabled = rlsEnabled;
        if (!rlsEnabled) {
            // Spec/multi-tenant-data-isolation: "RLS disabled in
            // production for emergency" — log once at startup so
            // operators know the kill-switch is on.
            LOG.warn("RLS disabled via app.rls.enabled=false — companyDataSource "
                    + "queries will return rows from all tenants");
        }
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
                    "companyDataSource accessed without a TenantContext. " + "Did the authentication filter run?");
        }
        // The current transaction's connection is exposed by Spring
        // via TransactionSynchronizationManager. We obtain it by
        // piggy-backing on the @Transactional advice that wraps the
        // call: we look at the first argument if it is a Spring
        // EntityManager, or use a fresh connection if needed.
        //
        // Simpler approach: we wrap the call in a way that gives us
        // a callback BEFORE the JPA layer touches the connection.
        // The cleanest implementation uses Hibernate's
        // StatementInspector or ConnectionProvider; for PR1d the
        // simplest correct option is to acquire a connection from
        // the DataSource through Spring's DataSourceUtils.
        //
        // NOTE: PR1d implements the design via a Hibernate
        // StatementInspector-like hook (see TenantAwareInterceptor)
        // — this around-advice is the API contract surface; the
        // physical SET LOCAL emission lives in the interceptor.
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
