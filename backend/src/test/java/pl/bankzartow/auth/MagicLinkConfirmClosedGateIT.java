package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * The closed-gate guarantee of the confirmation endpoint (task T-022, epic §8):
 * this context is the production configuration — both variables set but no
 * {@link MagicLinkCapability} contributed (the real mail adapter arrives only in
 * T-025, the limiter in T-024, e.g. after a provider-configuration rollback).
 * Even with a valid token sitting in the database the endpoint answers 404 and
 * the token is not touched.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MagicLinkConfirmClosedGateIT {

    @LocalServerPort
    private int port;

    @Autowired
    private LoginTokenService service;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        // Production-like: variables present, capabilities absent — gate closed.
        registry.add("app.auth.email", () -> "owner@example.test");
        registry.add("app.public-url", () -> "https://bank.example.test");
    }

    @Test
    void closedGateAnswers404AndLeavesAValidTokenUntouched() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> confirm = http.confirm(token);

        assertThat(confirm.statusCode())
                .as("with the gate closed the endpoint behaves as if it did not exist,"
                        + " even though a valid token is in the database")
                .isEqualTo(404);
        Long untouched = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is null and invalidated_at is null",
                Long.class);
        assertThat(untouched).as("the token was not consumed or invalidated").isEqualTo(1);
        assertThat(service.consume(token))
                .as("the token remains fully usable afterwards")
                .isTrue();
        assertThat(http.get("/api/auth/me").statusCode())
                .as("no session was established")
                .isEqualTo(401);
    }
}
