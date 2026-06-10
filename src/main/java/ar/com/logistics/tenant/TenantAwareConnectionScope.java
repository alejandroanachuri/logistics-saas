package ar.com.logistics.tenant;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal scope helper used by {@link RlsAspect} to thread the
 * current tenant id into a Hibernate StatementInspector or any other
 * downstream hook that needs it without resorting to a ThreadLocal
 * re-read.
 *
 * <p>PR1d keeps this as a thin wrapper; PR2 will register a
 * StatementInspector that calls {@link RlsAspect#setCurrentTenantOnConnection}
 * for every connection checked out of the {@code companyDataSource}
 * pool. The aspect still emits the GUC on the path that uses raw
 * JDBC (e.g. JdbcTemplate calls inside services that bypass JPA).
 */
final class TenantAwareConnectionScope {

    private static final Logger LOG = LoggerFactory.getLogger(TenantAwareConnectionScope.class);

    private TenantAwareConnectionScope() {
        // static utility
    }

    /**
     * Run the supplied callback. Reserved for future expansion
     * (e.g. registering a per-thread statement inspector key).
     */
    static Object run(UUID tenantId, ThrowingSupplier<Object> callback) throws Throwable {
        LOG.trace("RLS scope entered for tenant {}", tenantId);
        try {
            return callback.get();
        } finally {
            LOG.trace("RLS scope exited for tenant {}", tenantId);
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }
}
