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
 * credentials.
 *
 * <p>The credentials are bound straight from the environment and are genuinely
 * absent in the test JVM (no {@code APP_AUTH_*} variables, and
 * {@code application.properties} no longer declares {@code ${...}} placeholders).
 * Each test leaves exactly one of the two truly unset — not merely overridden with
 * a blank argument — so it reproduces the real "variable not configured" case. The
 * missing value binds to {@code null} and {@link AuthProperties} rejects it, which
 * fails startup.
 */
@Testcontainers
class MissingCredentialsStartupIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void applicationDoesNotStartWhenUsernameIsAbsent() {
        // Only the password hash is configured; APP_AUTH_USERNAME is truly unset.
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class);

        assertThatThrownBy(() -> application.run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--server.port=0",
                "--app.auth.password-hash={bcrypt}$2b$10$0123456789012345678901"))
                .hasStackTraceContaining("app.auth.username");
    }

    @Test
    void applicationDoesNotStartWhenPasswordHashIsAbsent() {
        // Only the username is configured; APP_AUTH_PASSWORD_HASH is truly unset.
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class);

        assertThatThrownBy(() -> application.run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--server.port=0",
                "--app.auth.username=tester"))
                .hasStackTraceContaining("app.auth.password-hash");
    }
}
