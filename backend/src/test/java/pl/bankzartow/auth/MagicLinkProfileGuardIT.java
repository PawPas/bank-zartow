package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Profile-configuration matrix for the magic-link feature (task T-021, P1).
 * The {@code dev & !prod} expression on {@link MagicLinkDevConfig} and
 * {@link MagicLinkDevPreviewController} must keep the mock beans, capabilities
 * and dev-preview absent in every profile combination that includes {@code prod}
 * or that simply does not activate {@code dev}.
 *
 * <p>Each nested class is a separate Spring context. Covered here:
 * <ul>
 *   <li>{@code prod} — explicit production profile
 *   <li>{@code test} — custom profile unrelated to dev
 *   <li>{@code dev,prod} — the critical combined case the {@code !prod}
 *       exclusion is designed to block
 *   <li>{@code prod,test} — a further mixed combination that must also stay off
 * </ul>
 *
 * <p>The no-profile (default production-like) case is covered by
 * {@link MagicLinkProductionGuardIT}; the pure {@code dev} case by
 * {@link MagicLinkDevPreviewIT}.
 */
class MagicLinkProfileGuardIT {

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("prod")
    class WithProdProfile {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
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
        void prodProfileHasNoMockBeansNoCapabilitiesAndGateIsClosed() {
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("no mailer bean in prod profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist under prod profile (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("no capability beans declared under prod profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist under prod profile (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed under prod profile")
                    .isFalse();
        }

        @Test
        void prodProfileRequestEndpointReturns404AndDevPreviewIsAbsent() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();

            HttpResponse<String> preview = http.get("/api/auth/magic-link/dev-preview");
            assertThat(preview.statusCode())
                    .as("dev-preview path is absent under prod profile")
                    .isEqualTo(404);
            assertThat(preview.body()).doesNotContain("#token=");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles("test")
    class WithTestProfile {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
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
        void testProfileHasNoMockBeansNoCapabilitiesAndGateIsClosed() {
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("no mailer bean in test profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist under test profile (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("no capability beans declared under test profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist under test profile (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed under test profile")
                    .isFalse();
        }

        @Test
        void testProfileRequestEndpointReturns404AndDevPreviewIsAbsent() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();

            HttpResponse<String> preview = http.get("/api/auth/magic-link/dev-preview");
            assertThat(preview.statusCode())
                    .as("dev-preview path is absent under test profile")
                    .isEqualTo(404);
            assertThat(preview.body()).doesNotContain("#token=");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles({"dev", "prod"})
    class WithDevAndProdProfiles {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
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
        void devAndProdCombinationExcludesMockCapabilitiesAndKeepsGateClosed() {
            // The '!prod' in @Profile("dev & !prod") prevents the mock and
            // capabilities from activating even when 'dev' is in the active profiles.
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("the !prod exclusion prevents the mock from registering (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist when prod is active alongside dev (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("no capability beans when prod overrides dev (!prod exclusion)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist when prod is active alongside dev (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed: dev+prod combination provides no capabilities")
                    .isFalse();
        }

        @Test
        void devAndProdRequestEndpointReturns404AndDevPreviewIsAbsent() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();

            HttpResponse<String> preview = http.get("/api/auth/magic-link/dev-preview");
            assertThat(preview.statusCode())
                    .as("dev-preview path is absent under dev+prod (I8)")
                    .isEqualTo(404);
            assertThat(preview.body()).doesNotContain("#token=");
        }
    }

    @Nested
    @SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
    @Import(TestcontainersConfiguration.class)
    @ActiveProfiles({"prod", "test"})
    class WithProdAndTestProfiles {

        @DynamicPropertySource
        static void properties(DynamicPropertyRegistry registry) {
            AuthTestFixtures.registerCredentials(registry);
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
        void prodAndTestCombinationHasNoMockBeansNoCapabilitiesAndGateIsClosed() {
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("no mailer bean in prod+test combination")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist under prod+test (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkCapability.class))
                    .as("no capability beans in prod+test combination")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist under prod+test (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed under prod+test combination")
                    .isFalse();
        }

        @Test
        void prodAndTestRequestEndpointReturns404AndDevPreviewIsAbsent() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();

            HttpResponse<String> preview = http.get("/api/auth/magic-link/dev-preview");
            assertThat(preview.statusCode())
                    .as("dev-preview path is absent under prod+test (I8)")
                    .isEqualTo(404);
            assertThat(preview.body()).doesNotContain("#token=");
        }
    }
}
