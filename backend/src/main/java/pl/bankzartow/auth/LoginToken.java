package pl.bankzartow.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * A single-use magic-link login token. The plain token is never persisted — only
 * its SHA-256 hex digest (invariant I1 of {@code docs/EPIC-MAGIC-LINK.md}). The
 * token is usable iff {@code usedAt} and {@code invalidatedAt} are both null and
 * {@code expiresAt} is in the future, judged against the injected {@link java.time.Clock}
 * (invariant I2). State transitions happen through conditional bulk updates in
 * {@link LoginTokenRepository}, never through entity setters, so consumption stays
 * atomic under concurrency (invariant I3).
 */
@Entity
@Table(name = "login_token")
public class LoginToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    protected LoginToken() {
    }

    LoginToken(String tokenHash, Instant createdAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }
}
