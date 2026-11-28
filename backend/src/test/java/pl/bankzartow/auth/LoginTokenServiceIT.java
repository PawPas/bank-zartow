package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
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
    void invalidatingASingleTokenFreesItsPoolSlot() {
        service.issue();
        String sacrificed = service.issue().token();
        service.issue();
        assertThat(service.issue().isIssued()).as("pool starts full").isFalse();

        assertThat(service.invalidate(sacrificed)).isTrue();

        assertThat(service.issue().isIssued())
                .as("invalidating one token frees exactly one slot")
                .isTrue();
        assertThat(countActiveRows()).isEqualTo(3);
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

    @Test
    void cleansUpSpentRowsWhenIssuing() {
        String used = service.issue().token();
        assertThat(service.consume(used)).isTrue();
        String invalidated = service.issue().token();
        assertThat(service.invalidate(invalidated)).isTrue();
        service.issue(); // stays active until the clock moves past its TTL
        clock.advance(Duration.ofMinutes(16));

        LoginTokenIssueResult fresh = service.issue();

        assertThat(fresh.isIssued()).isTrue();
        assertThat(countRows())
                .as("used, invalidated and expired rows are gone; only the fresh token remains")
                .isEqualTo(1);
        assertThat(countActiveRows()).isEqualTo(1);
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
