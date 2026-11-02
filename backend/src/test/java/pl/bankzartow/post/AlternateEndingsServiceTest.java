package pl.bankzartow.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

/**
 * Unit tests for the provider-neutral generation rules. Sources are plain
 * {@link Flux} fixtures (no Spring, no network), so each rule is exercised in
 * isolation with {@link StepVerifier}, including virtual time for the timeout.
 */
class AlternateEndingsServiceTest {

    private static final AlternateEndingsRequest REQUEST =
            new AlternateEndingsRequest("Wprowadzenie", "Oryginalna puenta");

    private static AlternateEnding ending(String text) {
        return new AlternateEnding(text);
    }

    private static AlternateEndingsService service(AlternateEndingsGenerator source,
                                                   int maxCount, int maxLength, Duration timeout) {
        return new AlternateEndingsService(source, maxCount, maxLength, timeout);
    }

    @Test
    void emitsProposalsWhenSourceWithinLimits() {
        AlternateEndingsService service = service(
                request -> Flux.just(ending("a"), ending("b"), ending("c")),
                5, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending("a"), ending("b"), ending("c"))
                .verifyComplete();
    }

    @Test
    void emitsConfiguredMaximumThenFailsWhenSourceExceedsCount() {
        // Source offers four proposals but the limit is three: the first three
        // pass, then the stream fails explicitly instead of silently completing.
        AlternateEndingsService service = service(
                request -> Flux.just(ending("a"), ending("b"), ending("c"), ending("d")),
                3, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending("a"), ending("b"), ending("c"))
                .expectError(AlternateEndingsException.LimitExceeded.class)
                .verify();
    }

    @Test
    void failsWhenProposalExceedsLength() {
        AlternateEndingsService service = service(
                request -> Flux.just(ending("ok"), ending("waytoolong")),
                10, 4, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending("ok"))
                .expectError(AlternateEndingsException.ProposalTooLong.class)
                .verify();
    }

    @Test
    void countsProposalLengthInUnicodeCodePointsNotUtf16Units() {
        // U+1F600: one code point, two UTF-16 units. Two emoji = 2 code points
        // (4 UTF-16 units) and three emoji = 3 code points. With a 3-code-point
        // limit, both must pass even though their String.length() (4 and 6)
        // exceeds the limit, proving length is counted in code points.
        String emoji = "😀";
        AlternateEndingsService service = service(
                request -> Flux.just(ending(emoji.repeat(2)), ending(emoji.repeat(3))),
                10, 3, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending(emoji.repeat(2)), ending(emoji.repeat(3)))
                .verifyComplete();
    }

    @Test
    void failsWhenProposalExceedsLengthInCodePoints() {
        String emoji = "😀";
        AlternateEndingsService service = service(
                request -> Flux.just(ending(emoji.repeat(4))),
                10, 3, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .consumeErrorWith(error -> {
                    assertThat(error).isInstanceOf(AlternateEndingsException.ProposalTooLong.class);
                    AlternateEndingsException.ProposalTooLong tooLong =
                            (AlternateEndingsException.ProposalTooLong) error;
                    assertThat(tooLong.length()).isEqualTo(4);
                    assertThat(tooLong.maxLength()).isEqualTo(3);
                })
                .verify();
    }

    @Test
    void failsWithTimeoutWhenGenerationExceedsTotalDeadlineDespiteSteadyEmission() {
        // A source that keeps emitting every 100ms forever. A per-item inactivity
        // timeout would never fire, but the total deadline (500ms) must. Virtual
        // time keeps the test instant. The high count limit ensures the timeout,
        // not the count rule, ends the stream.
        StepVerifier.withVirtualTime(() -> service(
                        request -> Flux.interval(Duration.ofMillis(100)).map(i -> ending("x" + i)),
                        1_000, 100, Duration.ofMillis(500))
                        .generate(REQUEST))
                .thenAwait(Duration.ofMillis(500))
                .expectNextCount(4)
                .expectError(AlternateEndingsException.Timeout.class)
                .verify();
    }

    @Test
    void failsWithTimeoutWhenSourceHangsInsteadOfBlocking() {
        // A source that never emits and never completes must not hang: the total
        // timeout ends it with a domain error.
        StepVerifier.withVirtualTime(() -> service(
                        request -> Flux.never(),
                        5, 100, Duration.ofMillis(500))
                        .generate(REQUEST))
                .thenAwait(Duration.ofMillis(500))
                .expectError(AlternateEndingsException.Timeout.class)
                .verify();
    }

    @Test
    void completesWithoutTimeoutWhenSourceFinishesInTime() {
        // The source finishes immediately; the deadline must be cancelled, so the
        // stream completes at once without waiting for (or firing) the 10s timeout.
        AlternateEndingsService service = service(
                request -> Flux.just(ending("a"), ending("b")),
                5, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending("a"), ending("b"))
                .verifyComplete();
    }

    @Test
    void mapsImmediateSourceErrorToDomainErrorWithPreservedCause() {
        IOException cause = new IOException("provider down");
        AlternateEndingsService service = service(
                request -> Flux.error(cause),
                5, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .consumeErrorWith(error -> {
                    assertThat(error).isInstanceOf(AlternateEndingsException.SourceFailed.class);
                    assertThat(error.getCause()).isSameAs(cause);
                })
                .verify();
    }

    @Test
    void mapsSynchronousSourceThrowToDomainErrorWithPreservedCause() {
        // The port throws directly from generate() instead of returning an errored
        // Flux. Thanks to Flux.defer this still ends the stream with the domain
        // error (cause preserved) rather than throwing out of service.generate.
        IllegalStateException cause = new IllegalStateException("provider misconfigured");
        AlternateEndingsService service = service(
                request -> {
                    throw cause;
                },
                5, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .consumeErrorWith(error -> {
                    assertThat(error).isInstanceOf(AlternateEndingsException.SourceFailed.class);
                    assertThat(error.getCause()).isSameAs(cause);
                })
                .verify();
    }

    @Test
    void mapsDeferredSourceErrorToDomainErrorWithoutFallback() {
        // The source emits one proposal, then fails. The already-emitted proposal
        // is kept, the failure is mapped to the domain error, and crucially no
        // further ("fallback") proposals appear after the error.
        IOException cause = new IOException("provider down mid-stream");
        AlternateEndingsService service = service(
                request -> Flux.concat(Flux.just(ending("a")), Flux.error(cause)),
                5, 100, Duration.ofSeconds(10));

        StepVerifier.create(service.generate(REQUEST))
                .expectNext(ending("a"))
                .expectError(AlternateEndingsException.SourceFailed.class)
                .verify();
    }

    @Test
    void cancellingSubscriptionStopsSourceGenerationThroughService() {
        // Cancellation must propagate through the core down to the source and stop
        // further work. A manually controlled virtual clock stays active after the
        // cancel so we can advance past later scheduled emissions and prove the
        // source produced nothing more (its scheduled delay task was disposed),
        // not merely that a downstream operator ignored late signals.
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.getOrSet();
        try {
            Duration interval = Duration.ofMillis(150);
            MockAlternateEndingsGenerator mock = new MockAlternateEndingsGenerator(3, interval);
            AlternateEndingsService service = service(mock, 5, 280, Duration.ofSeconds(10));

            List<AlternateEnding> received = new CopyOnWriteArrayList<>();
            Disposable subscription = service.generate(REQUEST).subscribe(received::add);

            // First proposal is produced after one interval.
            scheduler.advanceTimeBy(interval);
            assertThat(received).hasSize(1);
            assertThat(mock.emittedCount()).isEqualTo(1);

            // Cancel through the core; the source observes the cancellation.
            subscription.dispose();
            assertThat(mock.cancelled()).isTrue();

            // Clock still active: move well past the next scheduled emissions
            // (at 300ms and 450ms). Nothing more is produced.
            scheduler.advanceTimeBy(interval.multipliedBy(5));
            assertThat(received).hasSize(1);
            assertThat(mock.emittedCount()).isEqualTo(1);
        } finally {
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void rejectsNonPositiveLimits() {
        AlternateEndingsGenerator source = request -> Flux.empty();
        assertThatThrownBy(() -> service(source, 0, 100, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(source, 5, 0, Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service(source, 5, 100, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
