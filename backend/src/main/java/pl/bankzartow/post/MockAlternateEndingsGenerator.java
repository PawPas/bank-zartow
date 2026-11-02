package pl.bankzartow.post;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;

/**
 * Deterministic, network-free {@link AlternateEndingsGenerator}, the default
 * implementation in tests and the local profile. It derives a fixed list of
 * proposals from the request and streams them with a small delay between
 * elements, so ordering and cancellation behave like a real streaming provider
 * would. It needs no API key and performs no network or other I/O.
 *
 * <p>The generator exposes {@link #emittedCount()} and {@link #cancelled()} so a
 * test can prove that cancelling the subscription actually stops the source
 * (no further elements are produced), not merely that a downstream operator
 * ignores late signals.
 */
public class MockAlternateEndingsGenerator implements AlternateEndingsGenerator {

    private final int proposalCount;
    private final Duration interval;
    private final AtomicInteger emittedCount = new AtomicInteger();
    private final AtomicBoolean cancelled = new AtomicBoolean();

    public MockAlternateEndingsGenerator(int proposalCount, Duration interval) {
        this.interval = Objects.requireNonNull(interval, "interval");
        if (proposalCount <= 0) {
            throw new IllegalArgumentException("proposalCount must be positive, was " + proposalCount);
        }
        if (interval.isNegative()) {
            throw new IllegalArgumentException("interval must not be negative, was " + interval);
        }
        this.proposalCount = proposalCount;
    }

    @Override
    public Flux<AlternateEnding> generate(AlternateEndingsRequest request) {
        Objects.requireNonNull(request, "request");
        return Flux.fromIterable(proposalsFor(request))
                .delayElements(interval)
                .doOnNext(proposal -> emittedCount.incrementAndGet())
                .doOnCancel(() -> cancelled.set(true));
    }

    /** Number of proposals actually emitted so far across subscriptions. */
    public int emittedCount() {
        return emittedCount.get();
    }

    /** Whether a subscription was cancelled (the cancel hook fired). */
    public boolean cancelled() {
        return cancelled.get();
    }

    private List<AlternateEnding> proposalsFor(AlternateEndingsRequest request) {
        List<AlternateEnding> proposals = new ArrayList<>(proposalCount);
        for (int i = 1; i <= proposalCount; i++) {
            proposals.add(new AlternateEnding(
                    "Wariant %d dla puenty: %s".formatted(i, request.originalEnding())));
        }
        return proposals;
    }
}
