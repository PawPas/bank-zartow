package pl.bankzartow.joke;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

/**
 * A joke in the bank. {@code content} is the complete original joke (including its
 * own punchline); later tasks add status, tags and alternate punchlines without
 * ever overwriting this field (see {@code docs/EPIC-1.md}).
 *
 * <p>Timestamps are UTC {@link Instant}s (CONVENTIONS.md). {@code version} is an
 * optimistic-lock {@link Version} present from the first slice so every future
 * modifying operation can rely on it consistently.
 */
@Entity
@Table(name = "joke")
public class Joke {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected Joke() {
    }

    /**
     * Creates a new joke. {@code content} must already be normalized and non-blank;
     * validation lives in {@link JokeService}, not in the entity.
     */
    public Joke(String content, Instant createdAt) {
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }
}
