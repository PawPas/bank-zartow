package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;
import pl.bankzartow.auth.MagicLinkMailer.SendOutcome;

/**
 * Behaviour of {@code POST /api/auth/magic-link} with the gate open (task
 * T-021), on the real HTTP stack and PostgreSQL: enumeration-proof identical
 * responses (I6), token only for the allowed address, the confirm link in the
 * mail with the token in the URL fragment, the D-ML6 token fate after definite
 * and uncertain send failures, CSRF enforcement, and no token or address in the
 * logs (I5). The test configuration registers the mock mailer and declares both
 * gate capabilities explicitly, as the epic design mandates for test/dev.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class MagicLinkRequestIT {

    static final String ALLOWED_EMAIL = "owner@example.test";
    static final String FOREIGN_EMAIL = "intruder@example.test";
    static final String PUBLIC_URL = "https://bank.example.test";
    static final String LINK_PREFIX = PUBLIC_URL + "/login/confirm#token=";

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenGateWithMockMailer {
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
        registry.add("app.auth.email", () -> ALLOWED_EMAIL);
        registry.add("app.public-url", () -> PUBLIC_URL);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MockMagicLinkMailer mailer;

    @Autowired
    private LoginTokenRepository repository;

    @Autowired
    private MagicLinkProperties magicLinkProperties;

    @Autowired
    private Clock clock;

    @BeforeEach
    void resetState() {
        repository.deleteAll();
        mailer.reset();
    }

    @Test
    void allowedAddressReceivesMailWithTokenInUrlFragment(CapturedOutput output) throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLink("  Owner@Example.TEST ");

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(response.body()).isEmpty();
        assertThat(mailer.sentMails()).hasSize(1);
        MockMagicLinkMailer.SentMail mail = mailer.sentMails().get(0);
        assertThat(mail.recipient())
                .as("the submitted address is normalized (trim + lower case) before use")
                .isEqualTo(ALLOWED_EMAIL);
        assertThat(mail.link()).startsWith(LINK_PREFIX);
        assertThat(mail.link())
                .as("the token travels in the URL fragment, never in a query parameter")
                .doesNotContain("?");
        String token = mail.link().substring(LINK_PREFIX.length());
        assertThat(token).isNotBlank();
        assertThat(repository.findByTokenHash(LoginTokenCodec.sha256Hex(token)))
                .as("the mailed token corresponds to exactly the persisted row")
                .isPresent();
        assertThat(repository.count()).isEqualTo(1);
        assertThat(output.getAll())
                .as("the token and the full link never appear in the logs (I5)")
                .doesNotContain(token)
                .doesNotContain("#token=");
    }

    @Test
    void responseIsByteIdenticalAcrossAllOutcomes() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> allowed = http.postMagicLink(ALLOWED_EMAIL);
        HttpResponse<String> foreign = http.postMagicLink(FOREIGN_EMAIL);
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);
        HttpResponse<String> definiteFailure = http.postMagicLink(ALLOWED_EMAIL);
        mailer.respondWith(SendOutcome.UNCERTAIN);
        HttpResponse<String> uncertain = http.postMagicLink(ALLOWED_EMAIL);
        mailer.respondWith(SendOutcome.SENT);
        while (repository.countActive(clock.instant()) < magicLinkProperties.maxActiveTokens()) {
            assertThat(http.postMagicLink(ALLOWED_EMAIL).statusCode()).isEqualTo(202);
        }
        int mailsBeforeFullPool = mailer.sentMails().size();
        HttpResponse<String> fullPool = http.postMagicLink(ALLOWED_EMAIL);

        assertThat(allowed.statusCode()).isEqualTo(202);
        assertThat(allowed.body()).as("202 carries no body").isEmpty();
        for (HttpResponse<String> other : java.util.List.of(foreign, definiteFailure, uncertain, fullPool)) {
            assertThat(other.statusCode())
                    .as("status identical for allowed, foreign, send failure and full pool (I6)")
                    .isEqualTo(allowed.statusCode());
            assertThat(other.body())
                    .as("body byte-identical for allowed, foreign, send failure and full pool (I6)")
                    .isEqualTo(allowed.body());
        }
        assertThat(mailer.sentMails())
                .as("a full pool issues no token and sends no mail")
                .hasSize(mailsBeforeFullPool);
        assertThat(repository.countActive(clock.instant()))
                .isEqualTo(magicLinkProperties.maxActiveTokens());
    }

    @Test
    void foreignAddressIssuesNothingAndLeavesNoRow() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLink(FOREIGN_EMAIL);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(repository.count()).as("a foreign address leaves zero new rows").isZero();
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void definiteSendFailuresInvalidateTokensAndNeverFillThePool(CapturedOutput output)
            throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);

        int poolSize = magicLinkProperties.maxActiveTokens();
        for (int i = 0; i < poolSize; i++) {
            assertThat(http.postMagicLink(ALLOWED_EMAIL).statusCode())
                    .as("a definite send failure does not change the response (I6)")
                    .isEqualTo(202);
        }
        assertThat(repository.countActive(clock.instant()))
                .as("each definitely-failed send invalidates its fresh token (D-ML6)")
                .isZero();

        mailer.respondWith(SendOutcome.SENT);
        HttpResponse<String> afterFailures = http.postMagicLink(ALLOWED_EMAIL);

        assertThat(afterFailures.statusCode()).isEqualTo(202);
        assertThat(mailer.sentMails())
                .as("after N definite failures the pool is free, so the next request issues a token")
                .hasSize(1);
        assertThat(repository.countActive(clock.instant())).isEqualTo(1);
        assertThat(output.getAll())
                .as("a failed send leaves neither the token fragment nor the recipient in the logs (I5)")
                .doesNotContain("#token=")
                .doesNotContain(ALLOWED_EMAIL);
    }

    @Test
    void uncertainSendOutcomeKeepsTokenActive() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();
        mailer.respondWith(SendOutcome.UNCERTAIN);

        HttpResponse<String> response = http.postMagicLink(ALLOWED_EMAIL);

        assertThat(response.statusCode()).isEqualTo(202);
        assertThat(repository.countActive(clock.instant()))
                .as("an uncertain outcome keeps the token active until TTL (D-ML6)")
                .isEqualTo(1);
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void requestWithoutCsrfHeaderIsRejectedAndIssuesNoToken() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLinkWithoutCsrf(ALLOWED_EMAIL);

        assertThat(response.statusCode())
                .as("a POST without the CSRF header must be rejected")
                .isEqualTo(403);
        assertThat(repository.count()).isZero();
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void syntacticallyInvalidEmailIsRejectedWith400WithoutToken() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        HttpResponse<String> response = http.postMagicLink("not-an-email");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .as("errors use the ProblemDetail media type (CONVENTIONS.md)")
                .contains("application/problem+json");
        assertThat(repository.count()).isZero();
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void priorValidTokenRemainsActiveAfterSubsequentDefiniteSendFailure() throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();

        // T1: issued and mailed
        assertThat(http.postMagicLink(ALLOWED_EMAIL).statusCode()).isEqualTo(202);
        assertThat(mailer.sentMails()).hasSize(1);
        String firstToken = mailer.sentMails().get(0).link().substring(LINK_PREFIX.length());
        String firstTokenHash = LoginTokenCodec.sha256Hex(firstToken);

        // T2: issued then immediately invalidated because the mailer reports DEFINITE_FAILURE (D-ML6)
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);
        assertThat(http.postMagicLink(ALLOWED_EMAIL).statusCode()).isEqualTo(202);

        // T1 is still fully active: none of its terminal fields are set and it has not expired
        LoginToken t1 = repository.findByTokenHash(firstTokenHash).orElseThrow();
        assertThat(t1.getUsedAt())
                .as("T1 was not consumed")
                .isNull();
        assertThat(t1.getInvalidatedAt())
                .as("D-ML6 targets only the freshly issued T2; T1 must not be touched")
                .isNull();
        assertThat(t1.getExpiresAt())
                .as("T1 has not expired")
                .isAfter(clock.instant());

        // T2 was freshly issued and immediately invalidated; its row exists but is not active
        List<LoginToken> allRows = repository.findAll();
        LoginToken t2 = allRows.stream()
                .filter(t -> !firstTokenHash.equals(t.getTokenHash()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("T2 row not found in database"));
        assertThat(t2.getInvalidatedAt())
                .as("T2 was invalidated after the definite send failure (D-ML6)")
                .isNotNull();

        assertThat(repository.countActive(clock.instant()))
                .as("exactly one token (T1) remains active")
                .isEqualTo(1);
    }

    @Test
    void mailerExceptionWithRecipientInMessageDoesNotLeakToLogs(CapturedOutput output)
            throws Exception {
        MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
        http.bootstrapCsrf();
        // The exception intentionally carries the recipient to verify no log leak (I5)
        mailer.makeNextSendThrow(new RuntimeException("send failed for " + ALLOWED_EMAIL));

        HttpResponse<String> response = http.postMagicLink(ALLOWED_EMAIL);

        assertThat(response.statusCode()).as("exception is treated as UNCERTAIN, not an error").isEqualTo(202);
        assertThat(output.getAll())
                .as("RuntimeException message carrying the recipient must not appear in logs (I5)")
                .doesNotContain(ALLOWED_EMAIL);
    }
}
