package pl.bankzartow.post;

import java.time.Duration;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.util.function.Tuple2;

/**
 * Provider-neutral core of the alternate-endings feature. It wraps any
 * {@link AlternateEndingsGenerator} source (the mock today, a real adapter later)
 * and applies the shared generation rules; the source itself stays a dumb stream
 * producer.
 *
 * <p>Rules, in stream order:
 * <ul>
 *   <li><b>Source-error mapping.</b> Any non-domain error from the source is
 *       mapped to {@link AlternateEndingsException.SourceFailed}. There is no
 *       {@code onErrorResume}, retry or alternate source: a failure ends the
 *       stream, it never switches to another (paid) provider.</li>
 *   <li><b>Length limit.</b> A proposal longer than {@code maxLength} ends the
 *       stream with {@link AlternateEndingsException.ProposalTooLong}. The
 *       oversized proposal is never emitted trimmed or truncated. Length is
 *       measured in Unicode code points (so a proposal of three emoji counts as
 *       three characters, not six UTF-16 units), making the limit independent of
 *       surrogate-pair encoding.</li>
 *   <li><b>Count limit.</b> The first {@code maxCount} proposals pass through;
 *       if the source tries to emit one more, the stream ends with
 *       {@link AlternateEndingsException.LimitExceeded}. The stream is not
 *       silently completed (truncated) at {@code maxCount}: exceeding the limit
 *       is an explicit error so the caller can tell the source over-produced.</li>
 *   <li><b>Total timeout.</b> Measured from subscription over the whole stream,
 *       not as a per-item inactivity gap. If generation has not completed within
 *       {@code timeout}, the stream ends with
 *       {@link AlternateEndingsException.Timeout} rather than hanging. A stream
 *       that completes in time cancels the deadline and never sees this error.</li>
 * </ul>
 */
public class AlternateEndingsService {

    private final AlternateEndingsGenerator source;
    private final int maxCount;
    private final int maxLength;
    private final Duration timeout;

    public AlternateEndingsService(AlternateEndingsGenerator source,
                                   int maxCount,
                                   int maxLength,
                                   Duration timeout) {
        this.source = Objects.requireNonNull(source, "source");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        if (maxCount <= 0) {
            throw new IllegalArgumentException("maxCount must be positive, was " + maxCount);
        }
        if (maxLength <= 0) {
            throw new IllegalArgumentException("maxLength must be positive, was " + maxLength);
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive, was " + timeout);
        }
        this.maxCount = maxCount;
        this.maxLength = maxLength;
    }

    public Flux<AlternateEnding> generate(AlternateEndingsRequest request) {
        Objects.requireNonNull(request, "request");

        Flux<AlternateEnding> ruled = source.generate(request)
                // Map only source-origin errors; our own domain errors (added by
                // the rules below) are introduced after this operator and keep
                // their type. No fallback to another source.
                .onErrorMap(error -> !(error instanceof AlternateEndingsException),
                        AlternateEndingsException.SourceFailed::new)
                .handle(this::enforceLength)
                .index()
                .handle(this::enforceCount);

        // Total deadline from subscription: a companion that stays silent and then
        // errors after `timeout`. takeUntilOther relays source items until the
        // companion signals, propagating the timeout error on the deadline; when
        // the source finishes first the companion is cancelled, so an in-time
        // stream never errors and nothing hangs.
        Mono<AlternateEnding> deadline = Mono.delay(timeout)
                .then(Mono.error(() -> new AlternateEndingsException.Timeout(timeout)));
        return ruled.takeUntilOther(deadline);
    }

    private void enforceLength(AlternateEnding ending, SynchronousSink<AlternateEnding> sink) {
        String text = ending.text();
        int length = text.codePointCount(0, text.length());
        if (length > maxLength) {
            sink.error(new AlternateEndingsException.ProposalTooLong(length, maxLength));
        } else {
            sink.next(ending);
        }
    }

    private void enforceCount(Tuple2<Long, AlternateEnding> indexed,
                              SynchronousSink<AlternateEnding> sink) {
        if (indexed.getT1() >= maxCount) {
            sink.error(new AlternateEndingsException.LimitExceeded(maxCount));
        } else {
            sink.next(indexed.getT2());
        }
    }
}
