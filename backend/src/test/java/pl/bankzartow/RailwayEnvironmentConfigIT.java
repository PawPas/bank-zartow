package pl.bankzartow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.bankzartow.auth.AuthTestFixtures;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Proves the application boots from configuration supplied the way Railway supplies
 * it: as operating-system environment variables in {@code UPPER_SNAKE_CASE}. Railway
 * injects a {@code PORT} the app must bind, and the owner wires the datasource and the
 * login account through {@code SPRING_DATASOURCE_*} and {@code APP_AUTH_*} variables
 * (see {@code railway.json} and {@code docs/DEPLOY.md}).
 *
 * <p>The variables are placed in a real {@link SystemEnvironmentPropertySource}, so
 * Spring Boot's relaxed binding performs the exact {@code SPRING_DATASOURCE_URL ->
 * spring.datasource.url} and {@code APP_AUTH_USERNAME -> app.auth.username} mapping it
 * would perform for genuine environment variables — none of the dotted property names
 * are provided directly. No real secret is used: the database is a throwaway
 * Testcontainers instance and the credentials are ephemeral fixtures.
 */
@Testcontainers
class RailwayEnvironmentConfigIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static ConfigurableApplicationContext context;
    private static int railwayPort;

    @BeforeAll
    static void startWithRailwayEnvironment() throws Exception {
        railwayPort = reserveFreePort();

        Map<String, Object> railwayVariables = new HashMap<>();
        // Railway injects PORT; the app must listen on it (server.port=${PORT}).
        railwayVariables.put("PORT", Integer.toString(railwayPort));
        // Datasource wired from the PostgreSQL service variables.
        railwayVariables.put("SPRING_DATASOURCE_URL", POSTGRES.getJdbcUrl());
        railwayVariables.put("SPRING_DATASOURCE_USERNAME", POSTGRES.getUsername());
        railwayVariables.put("SPRING_DATASOURCE_PASSWORD", POSTGRES.getPassword());
        // Single-account login, set by the owner as Railway variables.
        railwayVariables.put("APP_AUTH_USERNAME", AuthTestFixtures.USERNAME);
        railwayVariables.put("APP_AUTH_PASSWORD_HASH", AuthTestFixtures.PASSWORD_HASH);
        // Production hardening flag the owner sets on Railway (HTTPS).
        railwayVariables.put("APP_SESSION_COOKIE_SECURE", "true");

        SystemEnvironmentPropertySource environmentSource = new SystemEnvironmentPropertySource(
                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, railwayVariables);

        SpringApplication application = new SpringApplication(BankZartowApplication.class);
        application.addInitializers(ctx ->
                ctx.getEnvironment().getPropertySources().addFirst(environmentSource));
        context = application.run();
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void listensOnThePortFromTheRailwayPortVariable() throws Exception {
        // A successful response on exactly railwayPort proves server.port resolved the
        // injected PORT variable; the server would be unreachable there otherwise.
        HttpResponse<String> response = get("http://localhost:" + railwayPort + "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void servesHealthcheckUpFromRailwayDatabaseVariables() throws Exception {
        // The healthcheck path configured in railway.json. UP requires a live
        // datasource, which only works if the SPRING_DATASOURCE_* variables bound.
        HttpResponse<String> response = get("http://localhost:" + railwayPort + "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = JsonMapper.builder().build().readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("UP");
    }

    private static HttpResponse<String> get(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static int reserveFreePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
