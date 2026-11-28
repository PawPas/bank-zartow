package pl.bankzartow.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle of magic-link login tokens (task T-020): issuance bounded by a pool
 * of active tokens, atomic consumption, and targeted invalidation. No HTTP here —
 * the endpoints arrive in T-021/T-022 and call these operations.
 *
 * <p>Time comes exclusively from the injected {@link Clock}, so TTL and expiry
 * are deterministic in tests. The plain token exists only in the returned
 * {@link LoginTokenIssueResult}; nothing here logs it or its hash (invariant I5).
 */
@Service
public class LoginTokenService {

    /**
     * Key of the transaction-scoped advisory lock serializing issuance. There is
     * a single account and therefore a single pool, so one constant key is
     * enough; the value is arbitrary but must stay unique within this database.
     */
    static final long POOL_LOCK_KEY = 7_020_2026L;

    private final LoginTokenRepository repository;
    private final MagicLinkProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public LoginTokenService(LoginTokenRepository repository, MagicLinkProperties properties,
            Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Issues a new token unless the pool of active tokens is already at its
     * configured limit; a full pool yields a "not issued" result without touching
     * any existing token (invariant I4 — issuing never invalidates earlier
     * tokens). The advisory lock serializes racing issuance transactions, so the
     * count-then-insert sequence cannot overshoot the limit. Spent rows (used,
     * invalidated or expired) are cleaned up on the way — the table stays tiny
     * without a scheduler.
     */
    @Transactional
    public LoginTokenIssueResult issue() {
        Instant now = clock.instant();
        repository.acquirePoolLock(POOL_LOCK_KEY);
        repository.deleteSpent(now);
        if (repository.countActive(now) >= properties.maxActiveTokens()) {
            return LoginTokenIssueResult.poolFull();
        }
        String token = LoginTokenCodec.generate(random);
        Instant expiresAt = now.plus(properties.tokenTtl());
        repository.save(new LoginToken(LoginTokenCodec.sha256Hex(token), now, expiresAt));
        return LoginTokenIssueResult.issued(token, expiresAt);
    }

    /**
     * Consumes the token atomically: a single conditional UPDATE, so exactly one
     * caller wins a race (invariant I3); an unknown, used, invalidated or expired
     * token is refused with no state change (invariant I2).
     */
    @Transactional
    public boolean consume(String token) {
        return repository.consume(LoginTokenCodec.sha256Hex(token), clock.instant()) == 1;
    }

    /**
     * Atomically consumes the token and invalidates all remaining active tokens
     * in one serialized transaction (invariant I3 + policy D-ML6). The pool lock
     * serializes concurrent confirmations: the first caller consumes its token and
     * invalidates the rest; any other caller whose own token was just invalidated
     * finds nothing to consume and returns {@code false}. Without the lock two
     * confirmations of different tokens could both succeed.
     *
     * <p>If invalidation fails the whole transaction rolls back, reverting the
     * consume UPDATE and leaving the token intact (no half-login).
     */
    @Transactional
    public boolean consumeAndInvalidateOthers(String token) {
        repository.acquirePoolLock(POOL_LOCK_KEY);
        Instant now = clock.instant();
        if (repository.consume(LoginTokenCodec.sha256Hex(token), now) != 1) {
            return false;
        }
        repository.invalidateAllActive(now);
        return true;
    }

    /**
     * Invalidates every remaining active token — called after a successful login
     * (T-022, default policy D-ML6). The just-consumed token is already inactive,
     * so it is not touched. Returns the number of invalidated tokens.
     */
    @Transactional
    public int invalidateOtherActive() {
        return repository.invalidateAllActive(clock.instant());
    }

    /**
     * Invalidates exactly the given token, freeing its pool slot — used by T-021
     * after a definite send failure (policy D-ML6). Returns whether a token was
     * actually invalidated.
     */
    @Transactional
    public boolean invalidate(String token) {
        return repository.invalidateByTokenHash(LoginTokenCodec.sha256Hex(token), clock.instant()) == 1;
    }
}
