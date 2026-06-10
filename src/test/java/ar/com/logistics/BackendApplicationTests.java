package ar.com.logistics;

import org.junit.jupiter.api.Test;

/**
 * Chore-only smoke test. PR0 lays down the Spring Boot skeleton but
 * does not yet wire DataSources, JPA, or security. A full
 * SpringBootTest is impossible at this point because there is no
 * Postgres to connect to (PR1 brings the first Testcontainers IT)
 * and no DataSource routing config yet.
 *
 * Once PR1 lands, the application context will be exercised by
 * MigrationsApplyIT and other integration tests. For now we keep a
 * trivial green test so the suite reports success and CI is happy.
 */
class BackendApplicationTests {

    @Test
    void packageLoads() {
        // Intentional no-op: validates that the test classpath
        // resolves. Compile failure = red, otherwise = green.
    }
}
