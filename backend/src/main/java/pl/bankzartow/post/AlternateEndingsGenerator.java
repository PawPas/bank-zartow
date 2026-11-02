package pl.bankzartow.post;

import reactor.core.publisher.Flux;

/**
 * Provider-neutral port that produces a stream of alternate ending proposals for
 * a given {@link AlternateEndingsRequest}.
 *
 * <p>This is the single abstraction every source implements: the deterministic
 * {@link MockAlternateEndingsGenerator} today and, later and independently, a
 * real AI provider adapter (T-012, gated by the T-008 go/no-go). The port knows
 * nothing about HTTP, SSE or any provider; the shared generation rules (count
 * and length limits, total timeout, source-error mapping, no automatic provider
 * switch) live in {@link AlternateEndingsService}, not here.
 *
 * <p>The returned {@link Flux} is cold: nothing is generated until it is
 * subscribed, and cancelling the subscription stops further generation.
 */
public interface AlternateEndingsGenerator {

    Flux<AlternateEnding> generate(AlternateEndingsRequest request);
}
