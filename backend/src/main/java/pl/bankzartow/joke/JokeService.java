package pl.bankzartow.joke;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.bankzartow.common.TextNormalizer;

/**
 * Application logic for the joke bank. Knows nothing about HTTP (CONVENTIONS.md):
 * it takes raw text, normalizes and validates it, and returns persisted entities.
 */
@Service
public class JokeService {

    private final JokeRepository repository;

    public JokeService(JokeRepository repository) {
        this.repository = repository;
    }

    /**
     * Creates a joke from raw user input. The content is normalized first; if it is
     * blank afterwards the joke is rejected and nothing is written.
     *
     * @throws BlankJokeContentException if the content is blank after normalization
     */
    @Transactional
    public Joke create(String rawContent) {
        String content = TextNormalizer.normalize(rawContent);
        if (content.isEmpty()) {
            throw new BlankJokeContentException();
        }
        return repository.save(new Joke(content, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<Joke> listNewestFirst() {
        return repository.findAllByOrderByCreatedAtDescIdDesc();
    }

    /**
     * Single joke by id.
     *
     * @throws JokeNotFoundException if no joke with this id exists
     */
    @Transactional(readOnly = true)
    public Joke get(long id) {
        return repository.findById(id).orElseThrow(() -> new JokeNotFoundException(id));
    }

    /**
     * Replaces the content of a joke, guarded by its optimistic-lock version. Only
     * the content (and the modification timestamp) changes; the version is bumped
     * by Hibernate at flush. A thrown exception rolls the transaction back, so a
     * rejected edit never changes data.
     *
     * <p>The explicit version check below catches a client whose data is already
     * stale when the request arrives. Two transactions racing past it are caught
     * by the {@code @Version} check at flush, which surfaces as Spring's
     * {@code OptimisticLockingFailureException} — both end up as 409.
     *
     * @throws JokeNotFoundException if no joke with this id exists
     * @throws JokeVersionConflictException if {@code expectedVersion} is stale
     * @throws BlankJokeContentException if the content is blank after normalization
     */
    @Transactional
    public Joke updateContent(long id, String rawContent, long expectedVersion) {
        Joke joke = repository.findById(id).orElseThrow(() -> new JokeNotFoundException(id));
        if (joke.getVersion() != expectedVersion) {
            throw new JokeVersionConflictException(id, expectedVersion, joke.getVersion());
        }
        String content = TextNormalizer.normalize(rawContent);
        if (content.isEmpty()) {
            throw new BlankJokeContentException();
        }
        joke.updateContent(content, Instant.now());
        return joke;
    }

    /**
     * Deletes a joke, guarded by its optimistic-lock version like
     * {@link #updateContent}. Hibernate additionally deletes with a
     * {@code WHERE version = ?} clause, so a racing concurrent change still
     * surfaces as an optimistic-lock failure instead of silently deleting.
     *
     * @throws JokeNotFoundException if no joke with this id exists
     * @throws JokeVersionConflictException if {@code expectedVersion} is stale
     */
    @Transactional
    public void delete(long id, long expectedVersion) {
        Joke joke = repository.findById(id).orElseThrow(() -> new JokeNotFoundException(id));
        if (joke.getVersion() != expectedVersion) {
            throw new JokeVersionConflictException(id, expectedVersion, joke.getVersion());
        }
        repository.delete(joke);
    }
}
