package pl.bankzartow.auth;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Wires the magic-link configuration (task T-020). The property names are fixed
 * by the epic design and read through {@link Environment}, so the environment
 * variables bind via relaxed binding ({@code APP_AUTH_EMAIL} →
 * {@code app.auth.email}, {@code APP_PUBLIC_URL} → {@code app.public-url}, …).
 * They cannot live in a single {@code @ConfigurationProperties} prefix because
 * the epic mandates names under two different prefixes.
 *
 * <p>No {@link MagicLinkCapability} bean is declared here or anywhere else in the
 * production configuration: the gate stays closed in production until T-024
 * (limiter) and T-025 (real delivery) contribute the capabilities. Test and dev
 * configurations declare them explicitly.
 */
@Configuration
public class MagicLinkConfig {

    @Bean
    MagicLinkProperties magicLinkProperties(Environment environment) {
        return MagicLinkProperties.from(
                environment.getProperty("app.auth.email"),
                environment.getProperty("app.public-url"),
                environment.getProperty("app.magic-link.ttl-minutes", Long.class),
                environment.getProperty("app.magic-link.max-active-tokens", Integer.class));
    }

    @Bean
    MagicLinkGate magicLinkGate(MagicLinkProperties properties,
            List<MagicLinkCapability> capabilities) {
        Set<MagicLinkCapability> declared = capabilities.isEmpty()
                ? EnumSet.noneOf(MagicLinkCapability.class)
                : EnumSet.copyOf(capabilities);
        return new MagicLinkGate(properties, declared);
    }
}
