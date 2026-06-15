package ar.com.logistics.tenant;

import java.util.UUID;

/**
 * ThreadLocal holder for the current request's {@link TenantContextEntry}.
 *
 * <p>Read by {@link RlsAspect} and (in a later PR) by the
 * DataSourceRoutingAspect. Written by the authentication layer
 * (PR4) after the JWT is parsed.
 *
 * <p>The {@link #clear()} method MUST be called in the
 * {@code afterCompletion} hook of the request lifecycle
 * (see {@code TenantContextInterceptor} in PR4) to prevent the
 * ThreadLocal from leaking into the next request that happens to
 * reuse the same worker thread.
 */
public final class TenantContext {

    private static final ThreadLocal<TenantContextEntry> CURRENT = new ThreadLocal<>();

    private TenantContext() {
        // static utility
    }

    /** Stores the entry for the current request thread. */
    public static void set(UUID tenantId, TenantContextEntry.Scope scope) {
        CURRENT.set(new TenantContextEntry(tenantId, scope));
    }

    /** Returns the current entry, or {@code null} if none is bound. */
    public static TenantContextEntry get() {
        return CURRENT.get();
    }

    /** Returns the current tenant id, or {@code null} if no entry is bound or scope is PLATFORM. */
    public static UUID currentTenantId() {
        TenantContextEntry entry = CURRENT.get();
        return entry == null ? null : entry.tenantId();
    }

    /** Clears the entry for the current request thread. */
    public static void clear() {
        CURRENT.remove();
    }
}
