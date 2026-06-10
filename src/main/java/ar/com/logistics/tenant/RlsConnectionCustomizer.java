package ar.com.logistics.tenant;

import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Emits {@code SET LOCAL app.current_tenant = '<uuid>'} on the JDBC
 * connection held by the active Spring transaction, so that Hibernate
 * statements executed via the {@code companyDataSource} pool are
 * filtered by the RLS policies installed by V8.
 *
 * <p>Why a {@link TransactionSynchronization} and not a Hibernate
 * {@code StatementInspector} or a {@code ConnectionProvider}?
 * <ul>
 *   <li>Hibernate 7's {@code StatementInspector} has no access to the
 *       underlying JDBC connection — it only sees the SQL string, not
 *       the connection that runs it. We need to write the GUC on the
 *       actual connection before any SQL hits the database.</li>
 *   <li>Wrapping the {@code HikariDataSource} in a custom
 *       {@code ConnectionProvider} is invasive: Boot 4 + Hibernate 7
 *       expect a specific {@code HikariDataSource} shape and the
 *       autoconfig has to discover the wrapper.</li>
 *   <li>A {@link TransactionSynchronization} is the cleanest Spring
 *       hook: it runs once the transaction is bound to a connection
 *       (in {@code beforeCompletion} the tx is still active so the GUC
 *       is in place for the next statement), it is auto-removed when
 *       the tx ends, and it works regardless of which persistence
 *       unit (company / system / platform) is in use.</li>
 * </ul>
 *
 * <p>The RLS emission is ONLY triggered for the {@code company}
 * DataSource, identified by checking {@link DataSourceContext#isCompany()}.
 * The other two pools (system, platform) are skipped: system uses
 * {@code app_admin} with {@code BYPASSRLS} (so the GUC is meaningless),
 * platform uses cross-tenant policies that don't reference the GUC.
 */
@Component
public class RlsConnectionCustomizer {

    private final RlsAspect rlsAspect;

    public RlsConnectionCustomizer(RlsAspect rlsAspect) {
        this.rlsAspect = rlsAspect;
    }

    /**
     * Register a transaction synchronization that will emit the
     * {@code SET LOCAL} GUC during {@link TransactionSynchronization#beforeCompletion()},
     * which runs while the transaction (and therefore the JDBC
     * connection) is still active.
     *
     * <p>Idempotent: calling this twice from the same thread is fine
     * because the synchronization list is bound to the active tx and
     * is cleared when the tx ends.
     */
    public void register() {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            // Outside a transaction, there is no JDBC connection to
            // tag. RlsAspect guards against this case (it requires a
            // tenant in TenantContext before letting a call proceed),
            // so reaching here is a programming error.
            throw new IllegalStateException("RlsConnectionCustomizer.register() called outside an active transaction");
        }
        if (!DataSourceContext.isCompany()) {
            // Not the company pool: skip silently. The aspect's
            // pointcut only fires for company-side repositories, so
            // the only realistic caller IS in the company context.
            return;
        }
        UUID tenantId = TenantContext.currentTenantId();
        if (tenantId == null) {
            throw new IllegalStateException(
                    "companyDataSource accessed without a TenantContext. Did the authentication filter run?");
        }
        TransactionSynchronizationManager.registerSynchronization(new TenantSynchronization(tenantId, rlsAspect));
    }

    /**
     * Per-tx synchronization. Holds the immutable tenant id captured
     * at registration time so the SET LOCAL statement is stable even
     * if the request thread's TenantContext is cleared by a different
     * synchronization in the same tx.
     */
    private static final class TenantSynchronization implements TransactionSynchronization {

        private final UUID tenantId;
        private final RlsAspect rlsAspect;

        TenantSynchronization(UUID tenantId, RlsAspect rlsAspect) {
            this.tenantId = tenantId;
            this.rlsAspect = rlsAspect;
        }

        @Override
        public void beforeCompletion() {
            // In beforeCompletion the tx is still bound to its
            // connection, so SET LOCAL is honored. We obtain the
            // connection via Spring's DataSourceUtils on the
            // companyDataSource. The Hikari physical connection is
            // the same one Hibernate will use for the next statement.
            try (java.sql.Connection conn =
                    org.springframework.jdbc.datasource.DataSourceUtils.getConnection(rlsAspect.companyDataSource())) {
                RlsAspect.setCurrentTenantOnConnection(conn, tenantId);
            } catch (java.sql.SQLException ex) {
                // Postgres rejects SET LOCAL outside a tx with 25001.
                // The aspect already ensures we are inside a tx; if
                // the connection is actually detached we let the
                // subsequent query fail loudly rather than silently
                // bypass RLS.
                throw new IllegalStateException("Failed to emit SET LOCAL app.current_tenant", ex);
            }
        }
    }
}
