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
 * {@link MagicLinkDevPreviewController} must keep the mock beans and dev
 * capabilities absent in every profile combination that includes {@code prod}.
 *
 * <p>Covered here (each as a nested Spring context):
 * <ul>
 *   <li>{@code prod} — explicit production profile
 *   <li>{@code test} — custom profile unrelated to dev
 *   <li>{@code dev,prod} — the critical combined case the {@code !prod}
 *       exclusion is designed to block
 * </ul>
 *
 * <p>The no-profile (default production-like) case is covered by
 * {@link MagicLinkProductionGuardIT}; the {@code dev} case is covered by
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
        void prodProfileHasNoMockBeansAndGateIsClosed() {
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("no mailer bean in prod profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist under prod profile (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist under prod profile (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed under prod profile")
                    .isFalse();
        }

        @Test
        void prodProfileRequestEndpointReturns404() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
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
        void testProfileHasNoMockBeansAndGateIsClosed() {
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("no mailer bean in test profile")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist under test profile (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist under test profile (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed under test profile")
                    .isFalse();
        }

        @Test
        void testProfileRequestEndpointReturns404() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
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
        void devAndProdCombinationExcludesMockAndKeepsGateClosed() {
            // The '!prod' in @Profile("dev & !prod") prevents the mock and
            // capabilities from activating even when 'dev' is in the active profiles.
            assertThat(context.getBeanNamesForType(MagicLinkMailer.class))
                    .as("the !prod exclusion prevents the mock from registering (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MockMagicLinkMailer.class))
                    .as("mock mailer must not exist when prod is active alongside dev (I8)")
                    .isEmpty();
            assertThat(context.getBeanNamesForType(MagicLinkDevPreviewController.class))
                    .as("dev-preview must not exist when prod is active alongside dev (I8)")
                    .isEmpty();
            assertThat(context.getBean(MagicLinkGate.class).isOpen())
                    .as("gate stays closed: dev+prod combination provides no capabilities")
                    .isFalse();
        }

        @Test
        void devAndProdRequestEndpointReturns404() throws Exception {
            MagicLinkRequestTestClient http = new MagicLinkRequestTestClient(port);
            http.bootstrapCsrf();

            assertThat(http.postMagicLink("owner@example.test").statusCode()).isEqualTo(404);
            assertThat(repository.count()).isZero();
        }
    }
}
