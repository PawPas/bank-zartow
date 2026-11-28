package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Guard test for the production configuration (task T-021, invariant I8): the
 * default (no-profile) configuration — exactly what production runs — contains
 * no mailer bean, no mock and no dev-preview channel, and even with both
 * variables set the gate stays closed because nothing contributes the delivery
 * and limiter capabilities until T-025/T-024. The request endpoint is therefore
 * inactive (404) and issues nothing.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class MagicLinkProductionGuardIT {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        // Variables alone must not enable the feature in production (fail-closed).
        registry.add("app.auth.email", () -> "owner@example.test");
        registry.add("app.public-url", () -> "https://bank.example.test");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private ApplicationContext context;

    @Autowired
    private LoginTokenRepository repository;

    @Test
    void productionConfigurationHasNoMockMailerAndNoDevPreview() {
        assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                .as("no mailer bean of any kind exists before the real adapter (T-025)")
                .isEmpty();
        assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                .as("the mock mailer bean must not exist in the production configuration (I8)")
                .isEmpty();
        assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                .as("the dev-preview channel must not exist in the production configuration (I8)")
                .isEmpty();
        assertThat(context.getBean(MagicLinkGate.class).isOpen())
                .as("variables alone never open the gate in production")
                .isFalse();
    }

    @Test
    void requestEndpointIsInactiveWithoutRealDelivery() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLink("owner@example.test");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void devPreviewPathDoesNotExistInProduction() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);

        HttpResponse<String> response = http.get("/api/auth/magic-link/dev-preview");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body())
                .as("the missing dev-preview path is a plain 404, not the SPA shell")
                .doesNotContain("<div id=\"root\"");
        assertThat(response.body()).doesNotContain("#token=");
    }
}
