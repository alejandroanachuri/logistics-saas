package ar.com.logistics.tenant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service or repository method as bound to a specific
 * DataSource key. The DataSourceRoutingAspect (PR2) reads this
 * annotation and sets {@link DataSourceContext} before the call.
 *
 * <p>Keys MUST match the {@code @Bean} names on the three
 * DataSource configs: {@link DataSourceContext#COMPANY},
 * {@link DataSourceContext#SYSTEM}, {@link DataSourceContext#PLATFORM}.
 *
 * <p>Declared here in PR1d even though the routing aspect itself
 * is wired in a later PR — the annotation is the contract and is
 * cheap to ship early so downstream PRs can already mark methods.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface UseDataSource {

    /** DataSource key. See {@link DataSourceContext} for valid values. */
    String value();
}
