package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.bankzartow.MutableClock;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Service-level concurrency tests for
 * {@link LoginTokenService#consumeAndInvalidateOthers}: rollback guarantee and
 * the deterministic pool-lock proof for different-token races.
 *
 * <p>Both tests spy on {@link LoginTokenRepository#invalidateAllActive} to
 * control the transaction from outside. {@code @BeforeEach} resets the spy so
 * each test starts with no stubbing.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ConsumeAndInvalidateRollbackIT.TestClockConfig.class})
class ConsumeAndInvalidateRollbackIT {

    private static final Instant BASE = Instant.parse("2026-11-28T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE);
        }
    }

    @MockitoSpyBean
    private LoginTokenRepository repository;

    @Autowired
    private LoginTokenService service;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        registry.add("app.magic-link.ttl-minutes", () -> "15");
        registry.add("app.magic-link.max-active-tokens", () -> "3");
    }

    @BeforeEach
    void resetDatabaseAndSpy() {
        jdbc.update("delete from login_token");
        reset(repository); // clear stubbing left by previous test
    }

    @Test
    void rollsBackConsumeWhenInvalidationThrows() {
        String token = service.issue().token();
        doThrow(new RuntimeException("simulated DB failure during invalidation"))
                .when(repository).invalidateAllActive(any(Instant.class));

        assertThatThrownBy(() -> service.consumeAndInvalidateOthers(token))
                .isInstanceOf(RuntimeException.class);

        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt)
                .as("transaction rolled back: consume UPDATE is reverted when invalidation throws")
                .isNull();

        // The token remains valid and can still be consumed normally.
        assertThat(service.consume(token)).isTrue();
    }

    /**
     * Deterministic proof that the pool lock serializes racing confirmations of
     * different tokens. The spy intercepts the first transaction's
     * {@code invalidateAllActive} call — after consume is done but before commit —
     * and pauses it. While it is paused the second thread calls
     * {@code consumeAndInvalidateOthers(tokenB)}; the test verifies via
     * {@code pg_locks} that the second thread is genuinely waiting for the pool
     * advisory lock (not just HTTP timing). Only after the first transaction is
     * released does the second thread proceed, finding its own token already
     * invalidated by the first transaction.
     *
     * <p>The test detects both regressions the reviewer specified:
     * <ul>
     *   <li>Removing the pool lock: B does not block → {@code awaitAdvisoryLockWaiters}
     *       times out.</li>
     *   <li>Moving the lock acquisition to after the consume: B executes its own
     *       consume before A invalidates → both return {@code true}, the
     *       {@code assertThat(resultB).isFalse()} assertion fails.</li>
     * </ul>
     */
    @Test
    void poolLockSerializesDifferentTokenConfirmationsWithVerifiedDatabaseWait()
            throws Exception {
        String tokenA = service.issue().token();
        String tokenB = service.issue().token();

        CountDownLatch consumedBeforeInvalidate = new CountDownLatch(1);
        CountDownLatch releaseInvalidate = new CountDownLatch(1);

        // Intercept thread A's invalidation: signal that consume is done, then park.
        // We cannot call callRealMethod() on a Spring Data JPA interface proxy; instead
        // we perform the same update via JdbcTemplate — which, running on thread A with
        // an active Spring transaction, uses the same underlying connection and therefore
        // participates in the same transaction as the preceding consume UPDATE.
        doAnswer(invocation -> {
            consumedBeforeInvalidate.countDown();
            if (!releaseInvalidate.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("timed out waiting for release in spy hook");
            }
            Instant ts = invocation.getArgument(0);
            return jdbc.update(
                    "update login_token set invalidated_at = ?"
                            + " where used_at is null and invalidated_at is null"
                            + " and expires_at > ?",
                    java.sql.Timestamp.from(ts), java.sql.Timestamp.from(ts));
        }).when(repository).invalidateAllActive(any(Instant.class));

        ExecutorService exec = Executors.newFixedThreadPool(2);
        try {
            // Thread A: acquire lock → consume tokenA → park at invalidation.
            Future<Boolean> futureA = exec.submit(() -> service.consumeAndInvalidateOthers(tokenA));

            // Wait until tokenA is consumed and thread A is parked before invalidation.
            assertThat(consumedBeforeInvalidate.await(10, TimeUnit.SECONDS))
                    .as("thread A consumed tokenA and reached the invalidation step")
                    .isTrue();

            // Thread A's transaction is still open: pool lock is held, consume UPDATE is
            // in the WAL but not yet committed. Start B — it must block on the pool lock
            // before its own first write (pg_advisory_xact_lock).
            Future<Boolean> futureB = exec.submit(() -> service.consumeAndInvalidateOthers(tokenB));

            // State-detect, not sleep: poll pg_locks until B appears as a lock waiter.
            awaitAdvisoryLockWaiters(1);
            assertThat(futureB.isDone())
                    .as("thread B is blocked on the pool advisory lock, not completed")
                    .isFalse();

            // Release A: invalidation runs, transaction commits
            // (tokenA marked used, tokenB marked invalidated).
            releaseInvalidate.countDown();

            boolean resultA = futureA.get(10, TimeUnit.SECONDS);
            boolean resultB = futureB.get(10, TimeUnit.SECONDS);

            assertThat(resultA).as("thread A consumed tokenA").isTrue();
            assertThat(resultB)
                    .as("thread B finds tokenB already invalidated by A's committed transaction")
                    .isFalse();

            Long usedRows = jdbc.queryForObject(
                    "select count(*) from login_token where used_at is not null", Long.class);
            assertThat(usedRows).as("exactly one token is consumed (tokenA)").isEqualTo(1);

            Long invalidatedRows = jdbc.queryForObject(
                    "select count(*) from login_token where invalidated_at is not null", Long.class);
            assertThat(invalidatedRows)
                    .as("tokenB is invalidated by A's transaction (not consumed)")
                    .isEqualTo(1);

        } finally {
            releaseInvalidate.countDown(); // release A if the test fails mid-way
            exec.shutdownNow();
        }
    }

    /**
     * Waits until {@code pg_locks} shows the expected number of transactions
     * blocked on the pool advisory lock. This is state detection, not timing:
     * the loop polls a database fact and fails loudly on timeout — which is
     * exactly what happens if {@link LoginTokenService#consumeAndInvalidateOthers}
     * stops taking the lock or moves it after the consume UPDATE.
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
            if (waiters != null && waiters >= expected) {
                return;
            }
            Thread.sleep(20);
        }
        fail("timed out waiting for " + expected + " thread(s) blocked on the pool advisory lock"
                + " — consumeAndInvalidateOthers() does not appear to hold"
                + " pg_advisory_xact_lock across both writes"
                + " (removing the lock or moving it after consume causes this to fail)");
    }
}
