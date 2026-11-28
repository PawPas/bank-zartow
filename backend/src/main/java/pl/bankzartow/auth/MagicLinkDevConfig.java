package pl.bankzartow.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Developer-only magic-link wiring (task T-021), active exclusively under the
 * {@code dev} profile ({@code SPRING_PROFILES_ACTIVE=dev}). Registers the
 * in-memory {@link MockMagicLinkMailer} and explicitly declares both gate
 * capabilities, as the epic design mandates for test/dev configurations
 * ({@code docs/EPIC-MAGIC-LINK.md} §8) — so a developer with
 * {@code APP_AUTH_EMAIL} and {@code APP_PUBLIC_URL} set locally can exercise the
 * flow end to end through {@link MagicLinkDevPreviewController}.
 *
 * <p>The default (production) configuration never activates this class, so no
 * mock mailer bean and no capabilities exist there: the gate stays closed until
 * the real adapter (T-025) and the rate limiter (T-024) contribute them
 * (invariant I8, guard-tested).
 */
@Configuration
@Profile("dev & !prod")
public class MagicLinkDevConfig {

    @Bean
    MockMagicLinkMailer mockMagicLinkMailer() {
        return new MockMagicLinkMailer();
    }

    /** The mock stands in for real delivery — dev only, never production. */
    @Bean
    MagicLinkCapability devDeliveryCapability() {
        return MagicLinkCapability.DELIVERY;
    }

    /**
     * Declared explicitly for local development (there is no real limiter until
     * T-024); acceptable only because the dev profile never runs in production.
     */
    @Bean
    MagicLinkCapability devLimiterCapability() {
        return MagicLinkCapability.LIMITER;
    }
}
