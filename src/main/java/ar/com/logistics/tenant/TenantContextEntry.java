package ar.com.logistics.tenant;

/**
 * Per-request tenant context.
 *
 * <p>Stores the tenant id parsed from the authenticated JWT and the
 * scope the JWT was issued for. Used by {@link RlsAspect} to emit
 * {@code SET LOCAL app.current_tenant = ?} on every transaction that
 * runs through the {@code companyDataSource} and by the (future)
 * DataSourceRoutingAspect to pick the correct DataSource for the
 * current request scope.
 *
 * <p>The record is the value held by a {@link ThreadLocal} on
 * {@link TenantContext}. It is immutable so concurrent reads from the
 * same request thread see a consistent snapshot.
 */
public record TenantContextEntry(java.util.UUID tenantId, Scope scope) {

    public enum Scope {
        /** COMPANY-scope JWT — tenantId is required. */
        COMPANY,
        /** PLATFORM-scope JWT — tenantId is null. */
        PLATFORM
    }

    public TenantContextEntry {
        if (scope == Scope.COMPANY && tenantId == null) {
            throw new IllegalArgumentException("COMPANY scope requires a non-null tenantId");
        }
        if (scope == Scope.PLATFORM && tenantId != null) {
            throw new IllegalArgumentException("PLATFORM scope must not carry a tenantId");
        }
    }
}
