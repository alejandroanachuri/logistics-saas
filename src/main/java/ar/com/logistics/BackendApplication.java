package ar.com.logistics;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Application entry point.
 *
 * <p>{@link EnableAspectJAutoProxy} is required so Spring can weave
 * the {@link ar.com.logistics.tenant.RlsAspect} bean (and any future
 * DataSourceRoutingAspect). Without it, the aspect class is just a
 * regular Spring bean and its {@code @Around} advice is never
 * invoked.
 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }
}
