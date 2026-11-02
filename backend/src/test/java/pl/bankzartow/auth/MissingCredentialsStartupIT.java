package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.bankzartow.BankZartowApplication;

/**
 * Guards the acceptance criterion "the application does not start without the login
 * credentials". A valid database is provided (so Flyway and the schema check pass)
 * and the app boots as a real servlet app so Spring Security consumes the
 * credentials; {@code app.auth.*} is then blanked with high-precedence command-line
 * arguments. Binding {@link AuthProperties} must reject the blank values and fail
 * startup. Blanking the credentials here is the whole point: it proves the secret
 * has no default.
 */
@Testcontainers
class MissingCredentialsStartupIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void applicationDoesNotStartWithoutCredentials() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class);

        // Command-line args outrank application.properties, so the blank credentials
        // win over the ${APP_AUTH_*} placeholders and the guard is what fires.
        assertThatThrownBy(() -> application.run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--server.port=0",
                "--app.auth.username=",
                "--app.auth.password-hash="))
                .hasStackTraceContaining("app.auth");
    }
}
