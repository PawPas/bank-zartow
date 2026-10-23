package pl.bankzartow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides a throwaway PostgreSQL instance for {@code *IT} tests. {@code @ServiceConnection}
 * wires Spring Boot's datasource to the container, so the real database (never H2) is used
 * for integration tests. Importing this configuration is what makes a test require Docker;
 * unit tests ({@code *Test}) never load it and therefore run without Docker.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer<?> postgresContainer() {
        return new PostgreSQLContainer<>("postgres:16-alpine");
    }
}
