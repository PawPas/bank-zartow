package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * A closed gate hides the request endpoint (task T-021): here the mailer and
 * both capabilities are present but {@code APP_AUTH_EMAIL} is missing, so
 * {@link MagicLinkGate} stays closed — the endpoint answers {@code 404}, issues
 * nothing, and the rest of the application (session bootstrap, password login,
 * protected API) behaves exactly as before. The missing-capability variant is
 * covered by {@link MagicLinkProductionGuardIT}; the gate combinatorics
 * themselves are unit-tested in T-020.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MagicLinkRequestGateClosedIT {

    @TestConfiguration(proxyBeanMethods = false)
    static class MockMailerWithCapabilities {
        @Bean
        MockMagicLinkMailer mockMagicLinkMailer() {
            return new MockMagicLinkMailer();
        }

        @Bean
        MagicLinkCapability deliveryCapability() {
            return MagicLinkCapability.DELIVERY;
        }

        @Bean
        MagicLinkCapability limiterCapability() {
            return MagicLinkCapability.LIMITER;
        }
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        // Deliberately no app.auth.email: one missing variable keeps the gate closed.
        registry.add("app.public-url", () -> "https://bank.example.test");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockMagicLinkMailer mailer;

    @Autowired
    private LoginTokenRepository repository;

    @Test
    void closedGateAnswers404AndIssuesNothing() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLink("owner@example.test");

        assertThat(response.statusCode())
                .as("with the gate closed the endpoint behaves as if it did not exist")
                .isEqualTo(404);
        assertThat(repository.count()).isZero();
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void restOfTheApplicationWorksAsBefore() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);

        assertThat(http.bootstrapSession().statusCode()).isEqualTo(200);
        assertThat(http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD).statusCode())
                .as("password login is untouched by a closed magic-link gate")
                .isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);
    }
}
