package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.MutableClock;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Behavioural coverage of the token lifecycle (task T-020) against a real
 * PostgreSQL via Testcontainers: hash-only storage (I1), refusal without state
 * change (I2), atomic consumption with exactly one race winner (I3), the bounded
 * active pool that never invalidates earlier tokens (I4), targeted invalidation,
 * opportunistic cleanup, and a deterministic injected clock — time moves by
 * {@link MutableClock}, never by sleeping.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, LoginTokenServiceIT.TestClockConfig.class})
class LoginTokenServiceIT {

    private static final Instant BASE = Instant.parse("2026-11-28T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE);
        }
    }

    @Autowired
    private LoginTokenService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private DataSource dataSource;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        // The reversible defaults (TTL 15 min, pool 3) made explicit, so the
        // assertions below are self-contained.
        registry.add("app.magic-link.ttl-minutes", () -> "15");
        registry.add("app.magic-link.max-active-tokens", () -> "3");
    }

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbc.update("delete from login_token");
        clock.setInstant(BASE);
    }

    /** Independent SHA-256 oracle so the test does not reuse the production codec. */
    private static String sha256Hex(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private long countRows() {
        Long count = jdbc.queryForObject("select count(*) from login_token", Long.class);
        return count == null ? -1 : count;
    }

    private long countActiveRows() {
        Long count = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is null and invalidated_at is null and expires_at > ?",
                Long.class, OffsetDateTime.ofInstant(clock.instant(), java.time.ZoneOffset.UTC));
        return count == null ? -1 : count;
    }

    @Test
    void storesOnlySha256HashNeverThePlainToken() throws Exception {
        LoginTokenIssueResult result = service.issue();

        assertThat(result.isIssued()).isTrue();
        String token = result.token();
        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");

        String storedHash = jdbc.queryForObject("select token_hash from login_token", String.class);
        assertThat(storedHash)
                .isEqualTo(sha256Hex(token))
                .isNotEqualTo(token)
                .hasSize(64)
                .matches("[0-9a-f]+");
        Long plainMatches = jdbc.queryForObject(
                "select count(*) from login_token where token_hash = ?", Long.class, token);
        assertThat(plainMatches).isZero();
    }

    @Test
    void persistsIssuedTokenAcrossIndependentRead() {
        LoginTokenIssueResult result = service.issue();

        // Independent JDBC read, no JPA caches involved.
        OffsetDateTime expiresAt = jdbc.queryForObject(
                "select expires_at from login_token", OffsetDateTime.class);
        assertThat(expiresAt).isNotNull();
        assertThat(expiresAt.toInstant()).isEqualTo(BASE.plus(Duration.ofMinutes(15)));
        assertThat(result.expiresAt()).isEqualTo(BASE.plus(Duration.ofMinutes(15)));
    }

    @Test
    void consumesValidTokenExactlyOnce() {
        String token = service.issue().token();

        assertThat(service.consume(token)).isTrue();
        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt).isNotNull();
        assertThat(usedAt.toInstant()).isEqualTo(BASE);

        assertThat(service.consume(token)).as("second consumption is refused").isFalse();
        OffsetDateTime usedAtAfter = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAtAfter).isNotNull();
        assertThat(usedAtAfter.toInstant())
                .as("refusal does not change state")
                .isEqualTo(BASE);
    }

    @Test
    void refusesExpiredTokenWithoutStateChange() {
        String token = service.issue().token();

        clock.advance(Duration.ofMinutes(15).plusSeconds(1));

        assertThat(service.consume(token)).isFalse();
        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt).as("expired token is not marked used").isNull();
    }

    @Test
    void refusesInvalidatedTokenWithoutStateChange() {
        String token = service.issue().token();
        assertThat(service.invalidate(token)).isTrue();

        assertThat(service.consume(token)).isFalse();
        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt).isNull();
    }

    @Test
    void refusesUnknownTokenWithoutStateChange() {
        service.issue();

        assertThat(service.consume("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA")).isFalse();
        Long used = jdbc.queryForObject(
                "select count(*) from login_token where used_at is not null", Long.class);
        assertThat(used).isZero();
    }

    @Test
    void exactlyOneWinnerWhenTwoThreadsConsumeTheSameToken() throws Exception {
        String token = service.issue().token();
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> racers = List.of(
                    () -> {
                        barrier.await();
                        return service.consume(token);
                    },
                    () -> {
                        barrier.await();
                        return service.consume(token);
                    });

            List<Future<Boolean>> outcomes = pool.invokeAll(racers);
            int wins = 0;
            for (Future<Boolean> outcome : outcomes) {
                if (outcome.get()) {
                    wins++;
                }
            }
            assertThat(wins).as("exactly one racer consumes the token").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Long used = jdbc.queryForObject(
                "select count(*) from login_token where used_at is not null", Long.class);
        assertThat(used).isEqualTo(1);
    }

    @Test
    void issuingNeverInvalidatesEarlierTokensAndFullPoolRefusesWithoutException() {
        LoginTokenIssueResult first = service.issue();
        LoginTokenIssueResult second = service.issue();
        LoginTokenIssueResult third = service.issue();
        assertThat(first.isIssued()).isTrue();
        assertThat(second.isIssued()).isTrue();
        assertThat(third.isIssued()).isTrue();

        LoginTokenIssueResult fourth = service.issue();

        assertThat(fourth.isIssued()).as("full pool signals 'not issued' as a value").isFalse();
        assertThat(countActiveRows())
                .as("earlier tokens stay active — issuing invalidates nothing (I4)")
                .isEqualTo(3);
        Long invalidated = jdbc.queryForObject(
                "select count(*) from login_token where invalidated_at is not null", Long.class);
        assertThat(invalidated).isZero();
        assertThat(service.consume(first.token()))
                .as("a token issued before the full-pool refusal still works")
                .isTrue();
    }

    @Test
    void poolLimitHoldsUnderParallelIssuance() throws Exception {
        int racers = 8;
        CyclicBarrier barrier = new CyclicBarrier(racers);
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        try {
            List<Callable<LoginTokenIssueResult>> tasks = new ArrayList<>();
            for (int i = 0; i < racers; i++) {
                tasks.add(() -> {
                    barrier.await();
                    return service.issue();
                });
            }

            List<Future<LoginTokenIssueResult>> outcomes = pool.invokeAll(tasks);
            int issued = 0;
            for (Future<LoginTokenIssueResult> outcome : outcomes) {
                if (outcome.get().isIssued()) {
                    issued++;
                }
            }
            assertThat(issued)
                    .as("parallel issuance never exceeds the pool limit")
                    .isEqualTo(3);
        } finally {
            pool.shutdownNow();
        }
        assertThat(countActiveRows()).isEqualTo(3);
        assertThat(countRows()).isEqualTo(3);
    }

    /**
     * The deterministic near-full-pool race required by T-020: with N−1 active
     * tokens, three issuance transactions are made to genuinely overlap — the
     * test holds the pool advisory lock in its own transaction, submits the
     * racers, and waits until {@code pg_locks} reports all three of them
     * blocked on that lock (verifiable overlap, not a sleep). Releasing the
     * lock lets them proceed: exactly one issues the last slot, the rest get
     * "not issued", and the earlier tokens remain untouched and usable. The
     * lock must also be transaction-scoped: free again once the racers commit.
     */
    @Test
    void exactlyOneIssueWinsFromNearlyFullPoolWithOverlappingTransactions() throws Exception {
        String first = service.issue().token();
        String second = service.issue().token();
        assertThat(countActiveRows()).as("pool starts at N-1").isEqualTo(2);

        int racers = 3;
        ExecutorService pool = Executors.newFixedThreadPool(racers);
        List<Future<LoginTokenIssueResult>> outcomes = new ArrayList<>();
        try {
            try (Connection holder = dataSource.getConnection()) {
                holder.setAutoCommit(false);
                try (Statement statement = holder.createStatement()) {
                    statement.execute(
                            "select pg_advisory_xact_lock(" + LoginTokenService.POOL_LOCK_KEY + ")");
                }
                try {
                    for (int i = 0; i < racers; i++) {
                        outcomes.add(pool.submit(service::issue));
                    }
                    awaitAdvisoryLockWaiters(racers);
                    for (Future<LoginTokenIssueResult> outcome : outcomes) {
                        assertThat(outcome.isDone())
                                .as("every issuance transaction is in flight, blocked on the pool lock")
                                .isFalse();
                    }
                } finally {
                    holder.rollback();
                }
            }

            int issued = 0;
            for (Future<LoginTokenIssueResult> outcome : outcomes) {
                if (outcome.get().isIssued()) {
                    issued++;
                }
            }
            assertThat(issued).as("exactly one racer takes the last pool slot").isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }

        Long touched = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is not null or invalidated_at is not null",
                Long.class);
        assertThat(touched).as("the race touches no earlier token").isZero();
        assertThat(countActiveRows()).isEqualTo(3);
        assertThat(service.consume(first))
                .as("a token issued before the race is still valid")
                .isTrue();
        assertThat(service.consume(second)).isTrue();

        Boolean lockFree = jdbc.queryForObject(
                "select pg_try_advisory_xact_lock(" + LoginTokenService.POOL_LOCK_KEY + ")",
                Boolean.class);
        assertThat(lockFree)
                .as("the pool lock is transaction-scoped: released when the issuing transactions ended")
                .isTrue();
    }

    /**
     * Waits until {@code pg_locks} shows the expected number of transactions
     * blocked on the pool advisory lock. This is state detection, not timing:
     * the loop polls a fact in the database and fails loudly on timeout — which
     * is exactly what happens if {@link LoginTokenService#issue()} stops taking
     * the lock (the racers then never wait and the overlap cannot be proven).
     */
    private void awaitAdvisoryLockWaiters(int expected) throws InterruptedException {
        long classid = LoginTokenService.POOL_LOCK_KEY >>> 32;
        long objid = LoginTokenService.POOL_LOCK_KEY & 0xffffffffL;
        long deadlineNanos = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadlineNanos) {
            Long waiters = jdbc.queryForObject(
                    "select count(*) from pg_locks where locktype = 'advisory'"
                            + " and cast(classid as bigint) = ? and cast(objid as bigint) = ?"
                            + " and not granted",
                    Long.class, classid, objid);
            if (waiters != null && waiters == expected) {
                return;
            }
            Thread.sleep(20);
        }
        fail("timed out waiting for " + expected + " transactions blocked on the pool advisory lock"
                + " — issue() does not appear to acquire pg_advisory_xact_lock");
    }

    @Test
    void invalidatesOtherActiveTokensAfterSuccessfulConsumption() {
        String winner = service.issue().token();
        service.issue();
        service.issue();
        assertThat(service.consume(winner)).isTrue();

        int invalidated = service.invalidateOtherActive();

        assertThat(invalidated).isEqualTo(2);
        Long stillActive = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is null and invalidated_at is null",
                Long.class);
        assertThat(stillActive).isZero();
        // The consumed token is used, not invalidated.
        Long consumedUntouched = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is not null and invalidated_at is null",
                Long.class);
        assertThat(consumedUntouched).isEqualTo(1);
    }

    @Test
    void invalidatesExactlyTheTargetTokenLeavingOthersUntouched() throws Exception {
        String first = service.issue().token();
        String target = service.issue().token();
        service.issue();
        assertThat(service.issue().isIssued()).as("pool starts full").isFalse();

        assertThat(service.invalidate(target)).isTrue();

        // Checked before any cleanup can run (no issue() in between): exactly the
        // target row carries invalidated_at, the other two rows are untouched.
        OffsetDateTime targetInvalidatedAt = jdbc.queryForObject(
                "select invalidated_at from login_token where token_hash = ?",
                OffsetDateTime.class, sha256Hex(target));
        assertThat(targetInvalidatedAt).isNotNull();
        assertThat(targetInvalidatedAt.toInstant()).isEqualTo(BASE);
        Long touchedOthers = jdbc.queryForObject(
                "select count(*) from login_token where token_hash <> ?"
                        + " and (used_at is not null or invalidated_at is not null)",
                Long.class, sha256Hex(target));
        assertThat(touchedOthers).as("no token other than the target is touched").isZero();

        assertThat(service.consume(target))
                .as("the invalidated target is refused on consumption")
                .isFalse();
        assertThat(service.issue().isIssued())
                .as("invalidating the target frees exactly one slot")
                .isTrue();
        assertThat(service.consume(first))
                .as("an untouched earlier token still works")
                .isTrue();
    }

    @Test
    void expiryDrivenByInjectedClockFreesThePool() {
        service.issue();
        service.issue();
        service.issue();
        assertThat(service.issue().isIssued()).isFalse();

        clock.advance(Duration.ofMinutes(16));

        assertThat(service.issue().isIssued())
                .as("expired tokens no longer occupy the pool — no sleeping, clock moved")
                .isTrue();
    }

    /**
     * Cleanup of used and invalidated rows must be independent of expiry: the
     * clock never moves in this test, so every row is still well within its TTL
     * when the cleanup runs. A cleanup that only deleted expired rows would
     * leave the table at four rows and fail here. The one still-active token is
     * the control row that must survive.
     */
    @Test
    void cleansUpUsedAndInvalidatedRowsBeforeTheirTtlKeepingTheActiveOne() throws Exception {
        String used = service.issue().token();
        String invalidated = service.issue().token();
        String control = service.issue().token();
        assertThat(service.consume(used)).isTrue();
        assertThat(service.invalidate(invalidated)).isTrue();
        assertThat(countRows())
                .as("consume/invalidate alone leave the spent rows in place — "
                        + "the setup has not cleaned them up early")
                .isEqualTo(3);

        LoginTokenIssueResult fresh = service.issue();

        assertThat(fresh.isIssued())
                .as("only the control token was active, so the pool had room")
                .isTrue();
        assertThat(countRows()).isEqualTo(2);
        List<String> remainingHashes =
                jdbc.queryForList("select token_hash from login_token", String.class);
        assertThat(remainingHashes).containsExactlyInAnyOrder(
                sha256Hex(control), sha256Hex(fresh.token()));
    }

    @Test
    void cleansUpExpiredRowsWhenIssuing() throws Exception {
        service.issue();
        clock.advance(Duration.ofMinutes(16));

        LoginTokenIssueResult fresh = service.issue();

        assertThat(fresh.isIssued()).isTrue();
        assertThat(countRows())
                .as("the expired row is gone; only the fresh token remains")
                .isEqualTo(1);
        String remainingHash =
                jdbc.queryForObject("select token_hash from login_token", String.class);
        assertThat(remainingHash).isEqualTo(sha256Hex(fresh.token()));
    }

    @Test
    void refusesConsumeExactlyAtTheExpiryInstant() {
        String token = service.issue().token();

        // Usability requires now strictly before expires_at; at the boundary the
        // token is already expired.
        clock.advance(Duration.ofMinutes(15));

        assertThat(service.consume(token)).isFalse();
        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt).isNull();
    }

    @Test
    void neverLogsTokenOrItsHash() throws Exception {
        Logger applicationLogger = (Logger) LoggerFactory.getLogger("pl.bankzartow");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Level previousLevel = applicationLogger.getLevel();
        applicationLogger.setLevel(Level.ALL);
        applicationLogger.addAppender(appender);
        String token;
        try {
            token = service.issue().token();
            service.issue();
            service.consume(token);
            service.invalidateOtherActive();
            service.invalidate(token);
        } finally {
            applicationLogger.detachAppender(appender);
            applicationLogger.setLevel(previousLevel);
        }

        String hash = sha256Hex(token);
        for (ILoggingEvent event : appender.list) {
            assertThat(event.getFormattedMessage())
                    .as("application logs never contain the token or its hash (I5)")
                    .doesNotContain(token)
                    .doesNotContain(hash);
        }
    }
}
