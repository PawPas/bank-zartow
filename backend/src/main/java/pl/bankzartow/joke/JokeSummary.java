package pl.bankzartow.joke;

import java.time.Instant;

/**
 * List item for {@code GET /api/jokes}. Carries the version so the list can feed
 * later version-aware operations (edit, status, tags) without an extra fetch. The
 * shape is extensible as more joke data arrives in later tasks (docs/EPIC-1.md §7).
 */
public record JokeSummary(Long id, String content, Instant createdAt, Long version) {

    public static JokeSummary from(Joke joke) {
        return new JokeSummary(joke.getId(), joke.getContent(), joke.getCreatedAt(), joke.getVersion());
    }
}
