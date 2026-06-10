package ar.com.logistics.tenant;

/**
 * ThreadLocal holder for the current request's target DataSource.
 *
 * <p>Read by the future DataSourceRoutingAspect to pick the right
 * Hikari pool, and by {@link RlsAspect} to decide whether to emit
 * {@code SET LOCAL app.current_tenant} (only for the
 * {@code company} key).
 *
 * <p>For PR1d the routing aspect itself is not yet implemented; the
 * key is set manually by the (future) authentication filter and by
 * tests. The holder exists so {@link RlsAspect} and any later code
 * can query it without a circular dependency.
 */
public final class DataSourceContext {

    /** DataSource keys — must match the @Bean names on the @Configuration classes. */
    public static final String COMPANY = "company";

    public static final String SYSTEM = "system";

    public static final String PLATFORM = "platform";

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private DataSourceContext() {
        // static utility
    }

    public static void set(String key) {
        CURRENT.set(key);
    }

    public static String get() {
        return CURRENT.get();
    }

    public static boolean isCompany() {
        return COMPANY.equals(CURRENT.get());
    }

    public static void clear() {
        CURRENT.remove();
    }
}
