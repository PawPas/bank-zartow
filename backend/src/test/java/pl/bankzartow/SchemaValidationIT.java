package pl.bankzartow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.hibernate.tool.schema.spi.SchemaManagementException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guards the acceptance criterion "the application does not start when the schema does not
 * match the entities". Flyway is disabled so the container stays empty; the application's own
 * {@code ddl-auto=validate} (from application.properties, not overridden here) must then fail
 * startup because the {@code application_info} table expected by
 * {@link pl.bankzartow.common.ApplicationInfo} is missing. Not overriding the property means
 * this test also fails if the validation config is ever dropped from the application.
 */
@Testcontainers
class SchemaValidationIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void applicationDoesNotStartWhenSchemaDoesNotMatchEntities() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.flyway.enabled=false",
                        "spring.main.web-application-type=none");

        assertThatThrownBy(application::run)
                .hasRootCauseInstanceOf(SchemaManagementException.class)
                .rootCause()
                .hasMessageContaining("missing table")
                .hasMessageContaining("application_info");
    }
}
