package pl.bankzartow;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guards the acceptance criterion "the application does not start when the schema does not
 * match the entities". Flyway is disabled so the container stays empty; with
 * {@code ddl-auto=validate} Hibernate must then fail startup because the {@code application_info}
 * table expected by {@link pl.bankzartow.common.ApplicationInfo} is missing.
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
                        "spring.jpa.hibernate.ddl-auto=validate",
                        "spring.main.web-application-type=none");

        assertThatThrownBy(application::run).isInstanceOf(Exception.class);
    }
}
