package pl.bankzartow.auth;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Top-level test configuration that satisfies all magic-link gate prerequisites
 * except the URL: mock mailer (DELIVERY capability path) and both declared
 * capabilities ({@code DELIVERY} + {@code LIMITER}). Import alongside
 * {@link pl.bankzartow.TestcontainersConfiguration} in nested-class Spring Boot
 * integration tests where URL validation is the only variable under test.
 *
 * <p>Must be top-level (not a nested class) so that {@code @Import} discovers it.
 * Spring Boot's test context bootstrapper scans only the outer enclosing class for
 * nested {@code @TestConfiguration} members, so inner static classes inside nested
 * {@code @SpringBootTest} classes are never registered.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GateFullPrerequisitesConfiguration {

    @Bean
    public MockMagicLinkMailer mockMagicLinkMailer() {
        return new MockMagicLinkMailer();
    }

    @Bean
    public MagicLinkCapability deliveryCapability() {
        return MagicLinkCapability.DELIVERY;
    }

    @Bean
    public MagicLinkCapability limiterCapability() {
        return MagicLinkCapability.LIMITER;
    }
}
