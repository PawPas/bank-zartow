package pl.bankzartow.joke;

import java.time.Instant;

/**
 * Response for a single joke (e.g. after creation). The shape is intentionally
 * extensible: later tasks add status, tags and alternate punchlines, so this is
 * not a final, frozen contract (docs/EPIC-1.md §7).
 */
public record JokeResponse(Long id, String content, Instant createdAt, Long version) {

    public static JokeResponse from(Joke joke) {
        return new JokeResponse(joke.getId(), joke.getContent(), joke.getCreatedAt(), joke.getVersion());
    }
}
