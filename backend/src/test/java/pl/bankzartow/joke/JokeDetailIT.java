package pl.bankzartow.joke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.bankzartow.TestcontainersConfiguration;
import pl.bankzartow.auth.AuthTestFixtures;

/**
 * Behavioural coverage for joke details, content editing and deletion with
 * optimistic locking (task T-014), against the real HTTP stack and a
 * Testcontainers PostgreSQL (never H2). Security is exercised for real:
 * authentication and CSRF are not bypassed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class JokeDetailIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JokeRepository repository;

    @Autowired
    private JokeService service;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
    }

    @BeforeEach
    void clearJokes() {
        repository.deleteAll();
    }

    private JokeHttpClient authenticatedClient() throws Exception {
        JokeHttpClient http = new JokeHttpClient(port);
        http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);
        return http;
    }

    private long createJoke(JokeHttpClient http, String content) throws Exception {
        HttpResponse<String> created =
                http.postJson("/api/jokes", "{\"content\":\"" + content + "\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        return ((Number) JsonPath.read(created.body(), "$.id")).longValue();
    }

    private static void assertProblemDetail(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .as("errors use the ProblemDetail media type (CONVENTIONS.md)")
                .contains("application/problem+json");
        assertThat(((Number) JsonPath.read(response.body(), "$.status")).intValue())
                .isEqualTo(status);
    }

    @Test
    void returnsDetailsOfExistingJoke() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Szczegolowy zart");

        HttpResponse<String> response = http.get("/api/jokes/" + id);

        assertThat(response.statusCode()).isEqualTo(200);
        String body = response.body();
        assertThat(((Number) JsonPath.read(body, "$.id")).longValue()).isEqualTo(id);
        assertThat((String) JsonPath.read(body, "$.content")).isEqualTo("Szczegolowy zart");
        assertThat((String) JsonPath.read(body, "$.createdAt")).isNotBlank();
        assertThat((String) JsonPath.read(body, "$.updatedAt")).isNotBlank();
        assertThat(((Number) JsonPath.read(body, "$.version")).longValue()).isZero();
    }

    @Test
    void returns404ProblemDetailForMissingJoke() throws Exception {
        JokeHttpClient http = authenticatedClient();

        assertProblemDetail(http.get("/api/jokes/424242"), 404);
    }

    @Test
    void editWithCurrentVersionSavesNewContentAndBumpsVersion() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Stara tresc");

        HttpResponse<String> response = http.putJson("/api/jokes/" + id,
                "{\"content\":\"Nowa tresc\",\"version\":0}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(response.body(), "$.content")).isEqualTo("Nowa tresc");
        assertThat(((Number) JsonPath.read(response.body(), "$.version")).longValue())
                .as("a successful edit bumps the optimistic-lock version")
                .isEqualTo(1);

        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content")).isEqualTo("Nowa tresc");
        assertThat(((Number) JsonPath.read(detail.body(), "$.version")).longValue()).isEqualTo(1);
    }

    @Test
    void editChangesOnlyContentAndLeavesOtherFieldsIntact() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Niezmienny poczatek");
        HttpResponse<String> before = http.get("/api/jokes/" + id);
        String createdAtBefore = JsonPath.read(before.body(), "$.createdAt");
        String updatedAtBefore = JsonPath.read(before.body(), "$.updatedAt");

        HttpResponse<String> response = http.putJson("/api/jokes/" + id,
                "{\"content\":\"Nowa puenta\",\"version\":0}");
        assertThat(response.statusCode()).isEqualTo(200);

        HttpResponse<String> after = http.get("/api/jokes/" + id);
        String body = after.body();
        assertThat(((Number) JsonPath.read(body, "$.id")).longValue()).isEqualTo(id);
        assertThat((String) JsonPath.read(body, "$.content")).isEqualTo("Nowa puenta");
        assertThat((String) JsonPath.read(body, "$.createdAt"))
                .as("editing must not touch the creation timestamp")
                .isEqualTo(createdAtBefore);
        assertThat(Instant.parse((String) JsonPath.read(body, "$.updatedAt")))
                .as("editing refreshes the modification timestamp")
                .isAfter(Instant.parse(updatedAtBefore));
    }

    @Test
    void secondEditWithStaleVersionGets409AndChangesNothing() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Oryginal");

        HttpResponse<String> first = http.putJson("/api/jokes/" + id,
                "{\"content\":\"Pierwsza edycja\",\"version\":0}");
        assertThat(first.statusCode()).isEqualTo(200);

        // The second edit still carries version 0, which the first edit made stale.
        HttpResponse<String> second = http.putJson("/api/jokes/" + id,
                "{\"content\":\"Druga edycja\",\"version\":0}");

        assertProblemDetail(second, 409);
        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content"))
                .as("a conflicting edit must not change the data")
                .isEqualTo("Pierwsza edycja");
        assertThat(((Number) JsonPath.read(detail.body(), "$.version")).longValue()).isEqualTo(1);
    }

    @Test
    void editOfMissingJokeGets404() throws Exception {
        JokeHttpClient http = authenticatedClient();

        assertProblemDetail(
                http.putJson("/api/jokes/424242", "{\"content\":\"x\",\"version\":0}"), 404);
    }

    @Test
    void editWithoutVersionGets400AndChangesNothing() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Bez wersji");

        HttpResponse<String> response =
                http.putJson("/api/jokes/" + id, "{\"content\":\"Nowa tresc\"}");

        assertProblemDetail(response, 400);
        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content")).isEqualTo("Bez wersji");
    }

    @Test
    void editWithBlankContentGets400AndChangesNothing() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Pelna tresc");

        // Non-breaking spaces: blank only after normalization, like in T-013.
        HttpResponse<String> response = http.putJson("/api/jokes/" + id,
                "{\"content\":\"\\u00A0\\u00A0\",\"version\":0}");

        assertThat(response.statusCode()).isEqualTo(400);
        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content")).isEqualTo("Pelna tresc");
        assertThat(((Number) JsonPath.read(detail.body(), "$.version")).longValue()).isZero();
    }

    @Test
    void deleteWithCurrentVersionRemovesJokeAndLaterGetIs404() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Do usuniecia");

        HttpResponse<String> response = http.delete("/api/jokes/" + id, "0");

        assertThat(response.statusCode()).isEqualTo(204);
        assertProblemDetail(http.get("/api/jokes/" + id), 404);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deleteAcceptsQuotedIfMatchValue() throws Exception {
        // If-Match is an ETag-shaped header, so a quoted version must work too.
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Cytowana wersja");

        assertThat(http.delete("/api/jokes/" + id, "\"0\"").statusCode()).isEqualTo(204);
        assertThat(repository.count()).isZero();
    }

    @Test
    void deleteWithStaleVersionGets409AndKeepsJoke() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Trwaly");
        assertThat(http.putJson("/api/jokes/" + id,
                "{\"content\":\"Trwaly po edycji\",\"version\":0}").statusCode()).isEqualTo(200);

        // Deleting with the pre-edit version must conflict, not silently delete.
        HttpResponse<String> response = http.delete("/api/jokes/" + id, "0");

        assertProblemDetail(response, 409);
        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat(detail.statusCode()).isEqualTo(200);
        assertThat((String) JsonPath.read(detail.body(), "$.content")).isEqualTo("Trwaly po edycji");
    }

    @Test
    void deleteOfMissingJokeGets404() throws Exception {
        JokeHttpClient http = authenticatedClient();

        assertProblemDetail(http.delete("/api/jokes/424242", "0"), 404);
    }

    @Test
    void deleteWithoutVersionHeaderGets400AndKeepsJoke() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Wymaga wersji");

        HttpResponse<String> response = http.deleteWithoutIfMatch("/api/jokes/" + id);

        assertProblemDetail(response, 400);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void deleteWithMalformedVersionHeaderGets400AndKeepsJoke() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Zla wersja");

        HttpResponse<String> response = http.delete("/api/jokes/" + id, "nie-liczba");

        assertProblemDetail(response, 400);
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void detailEndpointsRequireSession() throws Exception {
        JokeHttpClient owner = authenticatedClient();
        long id = createJoke(owner, "Prywatny");
        JokeHttpClient anonymous = new JokeHttpClient(port);

        assertThat(anonymous.get("/api/jokes/" + id).statusCode()).isEqualTo(401);
        assertThat(anonymous.putJson("/api/jokes/" + id,
                "{\"content\":\"Wlam\",\"version\":0}").statusCode()).isEqualTo(401);
        assertThat(anonymous.delete("/api/jokes/" + id, "0").statusCode()).isEqualTo(401);

        HttpResponse<String> detail = owner.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content"))
                .as("unauthenticated requests must not change the data")
                .isEqualTo("Prywatny");
    }

    @Test
    void modifyingRequestsWithoutCsrfAreForbidden() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Chroniony");

        assertThat(http.putJsonWithoutCsrf("/api/jokes/" + id,
                "{\"content\":\"Bez tokenu\",\"version\":0}").statusCode()).isEqualTo(403);
        assertThat(http.deleteWithoutCsrf("/api/jokes/" + id, "0").statusCode()).isEqualTo(403);

        HttpResponse<String> detail = http.get("/api/jokes/" + id);
        assertThat((String) JsonPath.read(detail.body(), "$.content")).isEqualTo("Chroniony");
        assertThat(((Number) JsonPath.read(detail.body(), "$.version")).longValue()).isZero();
    }

    @Test
    void concurrentEditsLetExactlyOneWinAndTheOtherGets409() throws Exception {
        JokeHttpClient http = authenticatedClient();
        long id = createJoke(http, "Wyscig");

        // Both edits carry the same (current) version and are released at the same
        // moment, so they race inside the database. Whichever transaction commits
        // first wins; the other must get 409, whether it lost on the in-transaction
        // version check or on the optimistic-lock check at flush.
        CyclicBarrier start = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = executor.submit(() -> {
                start.await();
                return http.putJson("/api/jokes/" + id,
                        "{\"content\":\"Edycja A\",\"version\":0}");
            });
            Future<HttpResponse<String>> second = executor.submit(() -> {
                start.await();
                return http.putJson("/api/jokes/" + id,
                        "{\"content\":\"Edycja B\",\"version\":0}");
            });
            HttpResponse<String> responseA = first.get();
            HttpResponse<String> responseB = second.get();

            List<Integer> statuses = List.of(responseA.statusCode(), responseB.statusCode());
            assertThat(statuses)
                    .as("exactly one concurrent edit may win; the other must conflict")
                    .containsExactlyInAnyOrder(200, 409);

            HttpResponse<String> winner = responseA.statusCode() == 200 ? responseA : responseB;
            HttpResponse<String> detail = http.get("/api/jokes/" + id);
            assertThat((String) JsonPath.read(detail.body(), "$.content"))
                    .as("the stored content is exactly the winning edit")
                    .isEqualTo((String) JsonPath.read(winner.body(), "$.content"));
            assertThat(((Number) JsonPath.read(detail.body(), "$.version")).longValue())
                    .as("only one edit was applied")
                    .isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void realOptimisticLockConflictRollsBackTheLateTransaction() {
        // A true concurrent-transaction conflict: both transactions read version 0,
        // so the in-service version check passes for both. The one that commits
        // later must fail on the @Version check at flush and roll back.
        long id = service.create("Oryginal").getId();

        TransactionTemplate late = new TransactionTemplate(transactionManager);
        TransactionTemplate fast = new TransactionTemplate(transactionManager);
        fast.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> late.executeWithoutResult(status -> {
            Joke stale = repository.findById(id).orElseThrow();
            assertThat(stale.getVersion()).isZero();
            // A faster, independent transaction edits and commits in the meantime.
            fast.executeWithoutResult(inner -> {
                Joke fresh = repository.findById(id).orElseThrow();
                fresh.updateContent("Szybsza edycja", Instant.now());
            });
            // The late transaction edits its stale snapshot; its commit must fail.
            stale.updateContent("Spozniona edycja", Instant.now());
        })).isInstanceOf(OptimisticLockingFailureException.class);

        Joke persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getContent())
                .as("the late transaction was rolled back, the fast edit survives")
                .isEqualTo("Szybsza edycja");
        assertThat(persisted.getVersion()).isEqualTo(1);
    }

    @Test
    void realOptimisticLockConflictRollsBackTheLateDelete() {
        // Same true-race shape for deletion: the delete transaction reads version 0,
        // a faster transaction edits and commits, and the versioned DELETE at flush
        // (WHERE version = 0) matches nothing — the joke must survive untouched.
        long id = service.create("Oryginal").getId();

        TransactionTemplate late = new TransactionTemplate(transactionManager);
        TransactionTemplate fast = new TransactionTemplate(transactionManager);
        fast.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> late.executeWithoutResult(status -> {
            Joke stale = repository.findById(id).orElseThrow();
            assertThat(stale.getVersion()).isZero();
            fast.executeWithoutResult(inner -> {
                Joke fresh = repository.findById(id).orElseThrow();
                fresh.updateContent("Szybsza edycja", Instant.now());
            });
            repository.delete(stale);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        Joke persisted = repository.findById(id).orElseThrow();
        assertThat(persisted.getContent())
                .as("the late delete was rolled back, the fast edit survives")
                .isEqualTo("Szybsza edycja");
        assertThat(persisted.getVersion()).isEqualTo(1);
    }
}
