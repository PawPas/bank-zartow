package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import pl.bankzartow.BankZartowApplication;

/**
 * Startup-level guarantees of the magic-link gate (task T-020): missing
 * variables or capabilities never prevent startup; the production-like default
 * configuration keeps the gate closed even with both variables set (nothing in
 * this task contributes capabilities); explicit test/dev capabilities open it;
 * and the password fail-closed startup check stays fully independent of any
 * magic-link configuration.
 */
@Testcontainers
class MagicLinkStartupIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Explicit capability declarations, as a test/dev configuration would do.
     * Deliberately NOT annotated with {@code @Configuration}: the application's
     * component scan of {@code pl.bankzartow} also sees test classes, and an
     * annotated nested config would leak the capabilities into every context.
     * Registered explicitly as a source instead ({@code @Bean} lite mode), so
     * only the one test that wants the gate open gets them.
     */
    static class ExplicitCapabilities {
        @Bean
        MagicLinkCapability deliveryCapability() {
            return MagicLinkCapability.DELIVERY;
        }

        @Bean
        MagicLinkCapability limiterCapability() {
            return MagicLinkCapability.LIMITER;
        }
    }

    private static String[] baseProperties(String... extra) {
        String[] base = {
                "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "spring.datasource.username=" + POSTGRES.getUsername(),
                "spring.datasource.password=" + POSTGRES.getPassword(),
                "spring.main.web-application-type=none",
                "app.auth.username=tester",
                "app.auth.password-hash={bcrypt}$2b$10$abcdefghijklmnopqrstuv",
        };
        String[] all = new String[base.length + extra.length];
        System.arraycopy(base, 0, all, 0, base.length);
        System.arraycopy(extra, 0, all, base.length, extra.length);
        return all;
    }

    @Test
    void startsWithoutAnyMagicLinkConfigurationAndKeepsGateClosed() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class)
                .properties(baseProperties());

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBean(MagicLinkGate.class).isOpen()).isFalse();
        }
    }

    @Test
    void keepsGateClosedInProductionLikeConfigEvenWithVariablesSet() {
        // Both variables present, but the production configuration contributes no
        // capabilities until T-024/T-025 — the feature must stay off (fail-closed).
        SpringApplicationBuilder application = new SpringApplicationBuilder(BankZartowApplication.class)
                .properties(baseProperties(
                        "app.auth.email=owner@example.test",
                        "app.public-url=https://bank.example.test"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBean(MagicLinkGate.class).isOpen()).isFalse();
        }
    }

    @Test
    void opensGateWithExplicitTestCapabilitiesAndCompleteVariables() {
        SpringApplicationBuilder application = new SpringApplicationBuilder(
                BankZartowApplication.class, ExplicitCapabilities.class)
                .properties(baseProperties(
                        "app.auth.email=owner@example.test",
                        "app.public-url=https://bank.example.test"));

        try (ConfigurableApplicationContext context = application.run()) {
            assertThat(context.getBean(MagicLinkGate.class).isOpen()).isTrue();
        }
    }

    @Test
    void passwordFailClosedStartupIsIndependentOfMagicLinkConfiguration() {
        // Full magic-link configuration, capabilities included, but no password
        // hash: startup must still fail — the epic does not touch D-ML5.
        SpringApplicationBuilder application = new SpringApplicationBuilder(
                BankZartowApplication.class, ExplicitCapabilities.class)
                .properties(
                        "spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                        "spring.datasource.username=" + POSTGRES.getUsername(),
                        "spring.datasource.password=" + POSTGRES.getPassword(),
                        "spring.main.web-application-type=none",
                        "app.auth.username=tester",
                        "app.auth.email=owner@example.test",
                        "app.public-url=https://bank.example.test");

        assertThatThrownBy(application::run)
                .hasStackTraceContaining("app.auth.password-hash");
    }
}
