package pl.bankzartow.joke;

import java.time.Instant;

/**
 * Details of a single joke for {@code GET /api/jokes/{id}} (and the body of a
 * successful edit). The shape is intentionally extensible: later tasks add
 * status, tags and alternate punchlines, so this is not a final, frozen
 * contract (docs/EPIC-1.md §7).
 */
public record JokeDetail(Long id, String content, Instant createdAt, Instant updatedAt, Long version) {

    public static JokeDetail from(Joke joke) {
        return new JokeDetail(joke.getId(), joke.getContent(), joke.getCreatedAt(),
                joke.getUpdatedAt(), joke.getVersion());
    }
}
