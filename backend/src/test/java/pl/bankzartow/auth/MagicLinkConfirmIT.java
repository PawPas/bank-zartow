package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.filter.OncePerRequestFilter;
import pl.bankzartow.MutableClock;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Behavioural coverage of the magic-link confirmation endpoint (task T-022) over
 * the real HTTP stack and a Testcontainers PostgreSQL, with the gate open via
 * explicitly declared test capabilities (as designed in T-020). Tokens are
 * issued directly with {@link LoginTokenService} — this task does not depend on
 * the mailer (T-021). Covered: token + CSRF → 204 and a session that works on
 * protected {@code /api/**}; anti-fixation for both session cases (rotation of
 * an existing anonymous session, creation when none exists); exactly one winner
 * in a confirmation race; one generic 401 for every refusal cause; invalidation
 * of the remaining tokens after success; GETs without side effects (I7); CSRF
 * protection; logout of the magic-link session via the existing endpoint; and
 * the global {@code Referrer-Policy: same-origin} header.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import({TestcontainersConfiguration.class, MagicLinkConfirmIT.TestClockConfig.class,
        MagicLinkConfirmIT.TestCapabilities.class, MagicLinkConfirmIT.AnonymousSessionSupport.class})
class MagicLinkConfirmIT {

    private static final Instant BASE = Instant.parse("2026-11-28T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE);
        }
    }

    /**
     * Explicit capability declarations (test/dev configuration per the epic §8),
     * so the gate is open without the mailer port or the limiter.
     * {@code @TestConfiguration} keeps them out of component scanning.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class TestCapabilities {
        @Bean
        MagicLinkCapability deliveryCapability() {
            return MagicLinkCapability.DELIVERY;
        }

        @Bean
        MagicLinkCapability limiterCapability() {
            return MagicLinkCapability.LIMITER;
        }
    }

    /**
     * Test-only servlet filter that creates an anonymous {@code HttpSession} on
     * demand. Nothing in the application creates a session for an anonymous
     * visitor (the public session bootstrap deliberately does not), so the
     * pre-existing-session fixation scenario needs this hook. A filter bean is
     * used instead of a controller so no {@code @Component}-annotated class leaks
     * into other contexts via component scanning.
     */
    @TestConfiguration(proxyBeanMethods = false)
    static class AnonymousSessionSupport {
        @Bean
        OncePerRequestFilter anonymousSessionFilter() {
            return new OncePerRequestFilter() {
                @Override
                protected void doFilterInternal(HttpServletRequest request,
                        HttpServletResponse response, FilterChain filterChain)
                        throws ServletException, IOException {
                    if ("/test-support/anonymous-session".equals(request.getRequestURI())) {
                        request.getSession(true);
                        response.setStatus(204);
                        return;
                    }
                    filterChain.doFilter(request, response);
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private LoginTokenService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MutableClock clock;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        registry.add("app.auth.email", () -> "owner@example.test");
        registry.add("app.public-url", () -> "https://bank.example.test");
        // The reversible defaults made explicit so the assertions are self-contained.
        registry.add("app.magic-link.ttl-minutes", () -> "15");
        registry.add("app.magic-link.max-active-tokens", () -> "3");
    }

    @BeforeEach
    void resetDatabaseAndClock() {
        jdbc.update("delete from login_token");
        clock.setInstant(BASE);
    }

    private long countUntouchedActiveRows() {
        Long count = jdbc.queryForObject(
                "select count(*) from login_token"
                        + " where used_at is null and invalidated_at is null",
                Long.class);
        return count == null ? -1 : count;
    }

    @Test
    void validTokenWithCsrfEstablishesSessionForProtectedApi() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> confirm = http.confirm(token);

        assertThat(confirm.statusCode()).isEqualTo(204);
        HttpResponse<String> me = http.get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"" + AuthTestFixtures.USERNAME + "\"");
        assertThat(http.get("/api/jokes").statusCode())
                .as("the session works on other protected /api/** endpoints too")
                .isEqualTo(200);
    }

    @Test
    void confirmationWithExistingAnonymousSessionRotatesTheSessionId() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        assertThat(http.get("/test-support/anonymous-session").statusCode()).isEqualTo(204);
        String before = http.sessionId();
        assertThat(before).as("precondition: an anonymous session exists").isNotBlank();
        http.bootstrapSession();

        assertThat(http.confirm(token).statusCode()).isEqualTo(204);

        String after = http.sessionId();
        assertThat(after)
                .as("the session cookie before and after confirmation must differ (anti-fixation)")
                .isNotBlank()
                .isNotEqualTo(before);
        assertThat(MagicLinkConfirmHttpClient
                .getReplayingSession(port, "/api/auth/me", before).statusCode())
                .as("the pre-confirmation session id must no longer be accepted")
                .isEqualTo(401);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void confirmationWithoutAnySessionCreatesAnAuthenticatedSession() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();
        assertThat(http.sessionId())
                .as("precondition: the public session bootstrap sets the CSRF cookie"
                        + " but does not create an HttpSession")
                .isNull();

        HttpResponse<String> confirm = http.confirm(token);

        assertThat(confirm.statusCode())
                .as("confirmation without a prior session must succeed, not fail"
                        + " on a missing session after the token was consumed")
                .isEqualTo(204);
        assertThat(http.sessionId()).as("a new session was created").isNotBlank();
        assertThat(http.get("/api/auth/me").statusCode())
                .as("the SecurityContext is persisted: the next request is authenticated")
                .isEqualTo(200);
    }

    @Test
    void secondUseOfTheSameTokenIsRefused() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient first = new MagicLinkConfirmHttpClient(port);
        first.bootstrapSession();
        assertThat(first.confirm(token).statusCode()).isEqualTo(204);

        MagicLinkConfirmHttpClient second = new MagicLinkConfirmHttpClient(port);
        second.bootstrapSession();
        HttpResponse<String> replay = second.confirm(token);

        assertThat(replay.statusCode()).isEqualTo(401);
        assertThat(second.get("/api/auth/me").statusCode())
                .as("the refused client has no authenticated session")
                .isEqualTo(401);
    }

    @Test
    void everyRefusalCauseSharesOneIndistinguishableGeneric401() throws Exception {
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> unknown = http.confirm("nieistniejacy-token-AAAAAAAAAAAAAAAAAAAAAA");

        String invalidatedToken = service.issue().token();
        assertThat(service.invalidate(invalidatedToken)).isTrue();
        HttpResponse<String> invalidated = http.confirm(invalidatedToken);

        String usedToken = service.issue().token();
        assertThat(service.consume(usedToken)).isTrue();
        HttpResponse<String> used = http.confirm(usedToken);

        String expiredToken = service.issue().token();
        clock.advance(Duration.ofMinutes(15).plusSeconds(1));
        HttpResponse<String> expired = http.confirm(expiredToken);

        for (HttpResponse<String> refusal : List.of(unknown, invalidated, used, expired)) {
            assertThat(refusal.statusCode()).isEqualTo(401);
            assertThat(refusal.headers().firstValue("Content-Type").orElse(""))
                    .as("errors use the ProblemDetail media type (CONVENTIONS.md)")
                    .contains("application/problem+json");
            assertThat(refusal.body())
                    .as("every refusal is byte-for-byte the same generic ProblemDetail,"
                            + " never revealing the cause")
                    .isEqualTo(unknown.body());
        }
        assertThat(unknown.body()).contains("\"status\":401");
        assertThat(unknown.body().toLowerCase())
                .doesNotContain("used").doesNotContain("expired").doesNotContain("invalid")
                .doesNotContain("zużyty").doesNotContain("wygasły").doesNotContain("unieważniony");
    }

    @Test
    void parallelConfirmationsOfOneTokenYieldExactlyOneSession() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient first = new MagicLinkConfirmHttpClient(port);
        MagicLinkConfirmHttpClient second = new MagicLinkConfirmHttpClient(port);
        first.bootstrapSession();
        second.bootstrapSession();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {
            List<Callable<Integer>> racers = List.of(
                    () -> {
                        barrier.await();
                        return first.confirm(token).statusCode();
                    },
                    () -> {
                        barrier.await();
                        return second.confirm(token).statusCode();
                    });
            List<Future<Integer>> outcomes = pool.invokeAll(racers);
            statuses = List.of(outcomes.get(0).get(), outcomes.get(1).get());
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 401);
        int authenticatedSessions = 0;
        for (MagicLinkConfirmHttpClient client : List.of(first, second)) {
            if (client.get("/api/auth/me").statusCode() == 200) {
                authenticatedSessions++;
            }
        }
        assertThat(authenticatedSessions)
                .as("exactly one racer ends up with an authenticated session")
                .isEqualTo(1);
        Long usedRows = jdbc.queryForObject(
                "select count(*) from login_token where used_at is not null", Long.class);
        assertThat(usedRows).isEqualTo(1);
    }

    @Test
    void successfulConfirmationInvalidatesTheRemainingActiveTokens() throws Exception {
        String winner = service.issue().token();
        String remaining = service.issue().token();
        service.issue();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();

        assertThat(http.confirm(winner).statusCode()).isEqualTo(204);

        Long invalidated = jdbc.queryForObject(
                "select count(*) from login_token where invalidated_at is not null", Long.class);
        assertThat(invalidated)
                .as("the two remaining active tokens are invalidated (policy D-ML6)")
                .isEqualTo(2);
        MagicLinkConfirmHttpClient other = new MagicLinkConfirmHttpClient(port);
        other.bootstrapSession();
        assertThat(other.confirm(remaining).statusCode())
                .as("an old mail no longer logs in after a successful confirmation")
                .isEqualTo(401);
    }

    @Test
    void getRequestsNeverChangeTokenState() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);

        HttpResponse<String> confirmPage = http.get("/login/confirm");
        assertThat(confirmPage.statusCode())
                .as("the confirmation page is the public SPA shell")
                .isEqualTo(200);
        assertThat(confirmPage.body()).contains("<div id=\"root\"");
        http.bootstrapSession();
        http.get(MagicLinkConfirmHttpClient.CONFIRM_PATH);

        assertThat(countUntouchedActiveRows())
                .as("no GET consumes or invalidates the token (I7)")
                .isEqualTo(1);
        assertThat(http.confirm(token).statusCode())
                .as("the token still works after all those GETs")
                .isEqualTo(204);
    }

    @Test
    void confirmationWithoutCsrfHeaderIsRejectedAndDoesNotConsumeTheToken() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> rejected = http.confirmWithoutCsrf(token);

        assertThat(rejected.statusCode())
                .as("a POST without the CSRF header must be rejected")
                .isEqualTo(403);
        assertThat(countUntouchedActiveRows())
                .as("the rejected POST did not touch the token")
                .isEqualTo(1);
        assertThat(http.confirm(token).statusCode())
                .as("with the CSRF header the same token still logs in")
                .isEqualTo(204);
    }

    @Test
    void magicLinkSessionLogsOutViaTheExistingLogoutEndpoint() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();
        assertThat(http.confirm(token).statusCode()).isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);

        // The SPA refreshes the session after login, which re-arms the CSRF cookie
        // for the logout POST (same lifecycle as password login in AuthIT).
        http.bootstrapSession();
        String sessionBeforeLogout = http.sessionId();
        assertThat(sessionBeforeLogout).isNotBlank();

        assertThat(http.logout().statusCode()).isEqualTo(204);

        assertThat(MagicLinkConfirmHttpClient
                .getReplayingSession(port, "/api/auth/me", sessionBeforeLogout).statusCode())
                .as("replaying the pre-logout session id proves server-side invalidation")
                .isEqualTo(401);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(401);
    }

    @Test
    void parallelConfirmationsOfDifferentTokensYieldExactlyOneSession() throws Exception {
        String tokenA = service.issue().token();
        String tokenB = service.issue().token();
        MagicLinkConfirmHttpClient clientA = new MagicLinkConfirmHttpClient(port);
        MagicLinkConfirmHttpClient clientB = new MagicLinkConfirmHttpClient(port);
        clientA.bootstrapSession();
        clientB.bootstrapSession();

        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Integer> statuses;
        try {
            List<Callable<Integer>> racers = List.of(
                    () -> {
                        barrier.await();
                        return clientA.confirm(tokenA).statusCode();
                    },
                    () -> {
                        barrier.await();
                        return clientB.confirm(tokenB).statusCode();
                    });
            List<Future<Integer>> outcomes = pool.invokeAll(racers);
            statuses = List.of(outcomes.get(0).get(), outcomes.get(1).get());
        } finally {
            pool.shutdownNow();
        }

        assertThat(statuses).containsExactlyInAnyOrder(204, 401);
        int authenticatedSessions = 0;
        for (MagicLinkConfirmHttpClient client : List.of(clientA, clientB)) {
            if (client.get("/api/auth/me").statusCode() == 200) {
                authenticatedSessions++;
            }
        }
        assertThat(authenticatedSessions)
                .as("pool lock serializes different-token confirmations: exactly one session")
                .isEqualTo(1);
        Long usedRows = jdbc.queryForObject(
                "select count(*) from login_token where used_at is not null", Long.class);
        assertThat(usedRows)
                .as("only the winning token is consumed")
                .isEqualTo(1);
    }

    @Test
    void csrfTokenIsRotatedAfterSuccessfulConfirmation() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);
        http.bootstrapSession();
        String csrfBefore = http.csrfToken();
        assertThat(csrfBefore).as("precondition: CSRF cookie is set after bootstrap").isNotBlank();

        assertThat(http.confirm(token).statusCode()).isEqualTo(204);

        String csrfAfter = http.csrfToken();
        assertThat(csrfAfter)
                .as("CSRF token must be rotated after magic-link confirmation (CsrfAuthenticationStrategy)")
                .isNotBlank()
                .isNotEqualTo(csrfBefore);

        // A request carrying the pre-confirmation CSRF header (now stale) with the
        // new XSRF-TOKEN cookie is rejected — proving that the old value is dead.
        HttpResponse<String> stale = http.confirmWithExplicitCsrf(
                "nieistniejacy-token-AAAAAAAAAAAAAAAAAAAAAA", csrfBefore);
        assertThat(stale.statusCode())
                .as("stale CSRF header with current cookie yields 403, not 401 — CSRF check is enforced")
                .isEqualTo(403);
    }

    @Test
    void bootstrapConfirmLogoutBootstrapReloginFullLifecycle() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);

        // 1. Bootstrap
        http.bootstrapSession();

        // 2. Magic-link confirm
        assertThat(http.confirm(token).statusCode()).isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);

        // 3. SPA bootstraps after login to re-arm CSRF (same as password login flow in AuthIT)
        http.bootstrapSession();

        // 4. Logout
        assertThat(http.logout().statusCode()).isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode())
                .as("logged out session is gone")
                .isEqualTo(401);

        // 5. Fresh bootstrap — new CSRF cookie, no session
        http.bootstrapSession();

        // 6. Re-login with password on the same client (same cookie jar)
        assertThat(http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD).statusCode())
                .isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode())
                .as("password login after magic-link logout works")
                .isEqualTo(200);
    }

    @Test
    void responsesCarrySameOriginReferrerPolicy() throws Exception {
        String token = service.issue().token();
        MagicLinkConfirmHttpClient http = new MagicLinkConfirmHttpClient(port);

        HttpResponse<String> bootstrap = http.bootstrapSession();
        assertThat(bootstrap.headers().firstValue("Referrer-Policy"))
                .as("API responses carry Referrer-Policy: same-origin")
                .hasValue("same-origin");

        HttpResponse<String> confirm = http.confirm(token);
        assertThat(confirm.statusCode()).isEqualTo(204);
        assertThat(confirm.headers().firstValue("Referrer-Policy")).hasValue("same-origin");

        MagicLinkConfirmHttpClient refusedClient = new MagicLinkConfirmHttpClient(port);
        refusedClient.bootstrapSession();
        HttpResponse<String> refused =
                refusedClient.confirm("nieistniejacy-token-AAAAAAAAAAAAAAAAAAAAAA");
        assertThat(refused.statusCode()).isEqualTo(401);
        assertThat(refused.headers().firstValue("Referrer-Policy")).hasValue("same-origin");

        assertThat(http.get("/").headers().firstValue("Referrer-Policy"))
                .as("static responses carry the header too")
                .hasValue("same-origin");
    }
}
