package pl.bankzartow.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@link LoginToken}. All state transitions are conditional bulk
 * statements evaluated by PostgreSQL, so correctness under concurrency comes from
 * the database, not from read-modify-write in Java: consumption is a single
 * conditional UPDATE with exactly one winner (invariant I3), and the active-pool
 * limit is enforced under a transaction-scoped advisory lock (invariant I4).
 */
public interface LoginTokenRepository extends JpaRepository<LoginToken, Long> {

    /**
     * Serializes concurrent issuance transactions. The lock is released
     * automatically at commit/rollback ({@code pg_advisory_xact_lock}), so the
     * count-then-insert sequence in {@link LoginTokenService#issue()} cannot
     * overshoot the pool limit even when issues race. The cast to text is only
     * there because JPA cannot map PostgreSQL's {@code void}.
     */
    @Query(value = "select cast(pg_advisory_xact_lock(:key) as text)", nativeQuery = true)
    String acquirePoolLock(@Param("key") long key);

    /** Number of usable tokens (not used, not invalidated, not expired) at {@code now}. */
    @Query("select count(t) from LoginToken t"
            + " where t.usedAt is null and t.invalidatedAt is null and t.expiresAt > :now")
    long countActive(@Param("now") Instant now);

    /**
     * Atomic consumption (invariant I3): marks the token used iff it is still
     * usable. Returns 1 for the single winner, 0 for everyone else (unknown,
     * already used, invalidated or expired) — with no state change on 0.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginToken t set t.usedAt = :now"
            + " where t.tokenHash = :tokenHash and t.usedAt is null"
            + " and t.invalidatedAt is null and t.expiresAt > :now")
    int consume(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** Invalidates every still-active token (post-login hygiene, policy D-ML6). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginToken t set t.invalidatedAt = :now"
            + " where t.usedAt is null and t.invalidatedAt is null and t.expiresAt > :now")
    int invalidateAllActive(@Param("now") Instant now);

    /** Invalidates exactly the given token (frees its pool slot), if not already invalidated. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update LoginToken t set t.invalidatedAt = :now"
            + " where t.tokenHash = :tokenHash and t.invalidatedAt is null")
    int invalidateByTokenHash(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

    /** Deletes spent rows: used, invalidated or expired. Called when issuing (no scheduler). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from LoginToken t"
            + " where t.usedAt is not null or t.invalidatedAt is not null or t.expiresAt <= :now")
    int deleteSpent(@Param("now") Instant now);

    Optional<LoginToken> findByTokenHash(String tokenHash);
}
