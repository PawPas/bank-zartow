package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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
 * HTTP integration coverage for every invalid {@code APP_PUBLIC_URL} form with
 * all other gate prerequisites satisfied: app.auth.email set, mock mailer
 * registered, both capabilities declared (task T-021 / KA6). A closed gate
 * must return 404, issue no login token and send no mail. Each nested class is a
 * separate Spring context differing only in the value of {@code app.public-url}.
 *
 * <p>{@link WithValidUrl} is a positive control: the same infrastructure opens
 * the gate for a valid URL and returns 202, so a missing mock bean or capability
 * cannot silently mask URL-validation regressions in the negative cases.
 *
 * <p>Prerequisites (mailer + capabilities) are provided by the top-level
 * {@link GateFullPrerequisitesConfiguration}, imported via {@code @Import} on
 * each nested class. A nested-class {@code @TestConfiguration} would not be
 * discovered by Spring Boot's test context bootstrapper, which scans only the
 * outer enclosing class.
 */
class MagicLinkInvalidUrlIT {

    // =========================================================================
    // Positive control — valid URL opens the gate
    // =========================================================================

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithValidUrl {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            registry.add("app.public-url", () -> "https://bank.example.test");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach
        void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void allPrerequisitesPresentGateOpensAndRequestReturns202() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("both capabilities present — this context is the reference setup")
                    .hasSize(2);
            assertThat(gate.isOpen())
                    .as("valid URL completes all prerequisites — positive control")
                    .isTrue();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(202);
            assertThat(repository.count()).isEqualTo(1);
            assertThat(mailer.sentMails()).hasSize(1);
        }
    }

    // =========================================================================
    // Negative cases — gate closed, 404, zero tokens, zero mails
    // =========================================================================

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithOpaqueUrl {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            // Opaque URI: no authority component, uri.getHost() is null.
            registry.add("app.public-url", () -> "https:bank.example.test");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("capabilities present — URL is the only invalid prerequisite")
                    .hasSize(2);
            assertThat(gate.isOpen())
                    .as("opaque URI has no host: URL validation rejects it, gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithSingleSlashUrl {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            // Single-slash: hierarchical but no authority, uri.getHost() is null.
            registry.add("app.public-url", () -> "https:/bank.example.test");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class)).hasSize(2);
            assertThat(gate.isOpen())
                    .as("single-slash URI has no authority: URL validation rejects it, gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithNonRootPath {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            registry.add("app.public-url", () -> "https://bank.example.test/app");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class)).hasSize(2);
            assertThat(gate.isOpen())
                    .as("non-root path would break the fixed /login/confirm contract — gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithQueryString {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            registry.add("app.public-url", () -> "https://bank.example.test?x=1");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class)).hasSize(2);
            assertThat(gate.isOpen())
                    .as("query string in public URL is not permitted — gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithFragment {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            registry.add("app.public-url", () -> "https://bank.example.test#frag");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class)).hasSize(2);
            assertThat(gate.isOpen())
                    .as("fragment in public URL is not permitted — gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import({TestcontainersConfiguration.class, GateFullPrerequisitesConfiguration.class})
    class WithUserInfo {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
            registry.add("app.auth.email", () -> "owner@example.test");
            registry.add("app.public-url", () -> "https://user:pass@bank.example.test");
        }

        @LocalServerPort private int port;
        @Autowired private MockMagicLinkMailer mailer;
        @Autowired private ApplicationContext context;
        @Autowired private MagicLinkGate gate;
        @Autowired private LoginTokenRepository repository;

        @BeforeEach void reset() { repository.deleteAll(); mailer.reset(); }

        @Test
        void gateClosedOnlyBecauseOfUrlRequestReturns404() throws Exception {
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class)).hasSize(2);
            assertThat(gate.isOpen())
                    .as("userinfo in public URL is not permitted — gate stays closed")
                    .isFalse();

            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();
            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
            assertThat(mailer.sentMails()).isEmpty();
        }
    }
}
