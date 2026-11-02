package pl.bankzartow.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

/**
 * Unit tests for the deterministic mock source. No Spring, no API key, no
 * network: the stream is built purely from the request.
 */
class MockAlternateEndingsGeneratorTest {

    private static final AlternateEndingsRequest REQUEST =
            new AlternateEndingsRequest("Wprowadzenie", "Oryginalna puenta");
    private static final Duration INTERVAL = Duration.ofMillis(150);

    private static AlternateEnding ending(int index) {
        return new AlternateEnding(
                "Wariant %d dla puenty: %s".formatted(index, REQUEST.originalEnding()));
    }

    @Test
    void emitsDeterministicProposalsWithoutApiKeyOrNetwork() {
        MockAlternateEndingsGenerator mock = new MockAlternateEndingsGenerator(3, INTERVAL);

        StepVerifier.withVirtualTime(() -> mock.generate(REQUEST))
                .thenAwait(INTERVAL)
                .expectNext(ending(1))
                .thenAwait(INTERVAL)
                .expectNext(ending(2))
                .thenAwait(INTERVAL)
                .expectNext(ending(3))
                .verifyComplete();

        assertThat(mock.emittedCount()).isEqualTo(3);
        assertThat(mock.cancelled()).isFalse();
    }

    @Test
    void cancellationStopsFurtherGeneration() {
        // Take the first proposal, then cancel before the second one's delay
        // elapses. The source must stop: the cancel hook fires and no further
        // proposal is produced (emitted count stays at one), proving the source
        // itself halts rather than a downstream operator merely ignoring signals.
        MockAlternateEndingsGenerator mock = new MockAlternateEndingsGenerator(3, INTERVAL);

        StepVerifier.withVirtualTime(() -> mock.generate(REQUEST))
                .thenAwait(INTERVAL)
                .expectNext(ending(1))
                .thenCancel()
                .verify();

        assertThat(mock.cancelled()).isTrue();
        assertThat(mock.emittedCount()).isEqualTo(1);
    }
}
