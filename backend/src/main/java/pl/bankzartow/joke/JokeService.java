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
}
