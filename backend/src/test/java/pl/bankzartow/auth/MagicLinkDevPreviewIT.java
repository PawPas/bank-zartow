package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * The dev-only link preview (task T-021): under the {@code dev} profile the
 * mock mailer and both capabilities come from {@link MagicLinkDevConfig}, the
 * gate opens with complete variables, and {@code GET
 * /api/auth/magic-link/dev-preview} returns the last captured confirm link for
 * manual local testing — the single, narrow, explicit exception to invariant I5,
 * existing only in test/dev. Its absence in production is guard-tested in
 * {@link MagicLinkProductionGuardIT}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("dev")
class MagicLinkDevPreviewIT {

    static final String ALLOWED_EMAIL = "owner@example.test";
    static final String PUBLIC_URL = "https://bank.example.test";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        registry.add("app.auth.email", () -> ALLOWED_EMAIL);
        registry.add("app.public-url", () -> PUBLIC_URL);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockMagicLinkMailer mailer;

    @Autowired
    private MagicLinkGate gate;

    @Test
    void devProfileOpensGateAndServesLastCapturedLink() throws Exception {
        assertThat(gate.isOpen())
                .as("the dev profile declares both capabilities explicitly, so the gate opens")
                .isTrue();

        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);

        assertThat(http.get("/api/auth/magic-link/dev-preview").statusCode())
                .as("before any mail is captured the preview has nothing to show")
                .isEqualTo(404);

        http.bootstrapCsrf();
        assertThat(http.postMagicLink(ALLOWED_EMAIL).statusCode()).isEqualTo(202);

        HttpResponse<String> preview = http.get("/api/auth/magic-link/dev-preview");

        assertThat(preview.statusCode())
                .as("the dev preview is reachable without a session for manual local testing")
                .isEqualTo(200);
        MockMagicLinkMailer.SentMail mail = mailer.lastMail().orElseThrow();
        assertThat(mail.link()).startsWith(PUBLIC_URL + "/login/confirm#token=");
        assertThat(preview.body())
                .as("the preview returns exactly the last captured link")
                .contains("\"link\":\"" + mail.link() + "\"");
    }
}
