package pl.bankzartow.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Verifies the default wiring: the mock is the default generator and the service
 * bean is built from the configured generator and limits. Beans are invoked
 * directly (no Spring context needed), which is enough to document the default.
 */
class PostConfigurationTest {

    private static final AlternateEndingsProperties PROPERTIES =
            new AlternateEndingsProperties(5, 280, Duration.ofSeconds(10), 3, Duration.ofMillis(150));
    private static final AlternateEndingsRequest REQUEST =
            new AlternateEndingsRequest("Wprowadzenie", "Oryginalna puenta");

    private final PostConfiguration configuration = new PostConfiguration();

    @Test
    void defaultGeneratorIsMock() {
        AlternateEndingsGenerator generator =
                configuration.mockAlternateEndingsGenerator(PROPERTIES);

        assertThat(generator).isInstanceOf(MockAlternateEndingsGenerator.class);
    }

    @Test
    void serviceBuiltOnDefaultMockProducesProposals() {
        AlternateEndingsGenerator generator =
                configuration.mockAlternateEndingsGenerator(PROPERTIES);
        AlternateEndingsService service =
                configuration.alternateEndingsService(generator, PROPERTIES);

        StepVerifier.withVirtualTime(() -> service.generate(REQUEST))
                .thenAwait(Duration.ofMillis(150).multipliedBy(3))
                .expectNextCount(3)
                .verifyComplete();
    }
}
