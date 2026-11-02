package pl.bankzartow.joke;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pl.bankzartow.TestcontainersConfiguration;
import pl.bankzartow.auth.AuthTestFixtures;

/**
 * Behavioural coverage for the joke bank (task T-013) against the real HTTP stack
 * and a Testcontainers PostgreSQL (never H2). Security is exercised for real:
 * authentication and CSRF are not bypassed.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class JokeIT {

    @LocalServerPort
    private int port;

    @Autowired
    private JokeRepository repository;

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

    @Test
    void createsJokeAndReturns201WithIdContentAndVersion() throws Exception {
        JokeHttpClient http = authenticatedClient();

        HttpResponse<String> response =
                http.postJson("/api/jokes", "{\"content\":\"Dlaczego programista?\"}");

        assertThat(response.statusCode()).isEqualTo(201);
        String body = response.body();
        assertThat(((Number) JsonPath.read(body, "$.id")).longValue()).isPositive();
        assertThat((String) JsonPath.read(body, "$.content")).isEqualTo("Dlaczego programista?");
        assertThat((String) JsonPath.read(body, "$.createdAt")).isNotBlank();
        assertThat(((Number) JsonPath.read(body, "$.version")).longValue())
                .as("a fresh entity starts at optimistic-lock version 0")
                .isZero();
    }

    @Test
    void rejectsBlankContentWith400ProblemDetailAndCreatesNothing() throws Exception {
        JokeHttpClient http = authenticatedClient();

        HttpResponse<String> response = http.postJson("/api/jokes", "{\"content\":\"   \"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .as("errors use the ProblemDetail media type (CONVENTIONS.md)")
                .contains("application/problem+json");
        assertThat(((Number) JsonPath.read(response.body(), "$.status")).intValue()).isEqualTo(400);
        assertThat(repository.count())
                .as("a rejected joke must not be persisted")
                .isZero();
    }

    @Test
    void rejectsContentThatIsBlankOnlyAfterNormalizationWith400() throws Exception {
        JokeHttpClient http = authenticatedClient();

        // Two non-breaking spaces: not blank to a plain @NotBlank check, but blank
        // once the shared TextNormalizer collapses them.
        HttpResponse<String> response = http.postJson("/api/jokes", "{\"content\":\"\\u00A0\\u00A0\"}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void rejectsMissingContentWith400() throws Exception {
        JokeHttpClient http = authenticatedClient();

        HttpResponse<String> response = http.postJson("/api/jokes", "{}");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(repository.count()).isZero();
    }

    @Test
    void listReturnsJokesNewestFirst() throws Exception {
        JokeHttpClient http = authenticatedClient();
        http.postJson("/api/jokes", "{\"content\":\"Najstarszy\"}");
        http.postJson("/api/jokes", "{\"content\":\"Najnowszy\"}");

        HttpResponse<String> response = http.get("/api/jokes");

        assertThat(response.statusCode()).isEqualTo(200);
        List<String> contents = JsonPath.read(response.body(), "$[*].content");
        assertThat(contents)
                .as("the most recently created joke comes first")
                .containsExactly("Najnowszy", "Najstarszy");
    }

    @Test
    void apiRequiresSession() throws Exception {
        JokeHttpClient anonymous = new JokeHttpClient(port);

        assertThat(anonymous.get("/api/jokes").statusCode())
                .as("listing jokes without a session must be 401")
                .isEqualTo(401);
        assertThat(anonymous.postJson("/api/jokes", "{\"content\":\"x\"}").statusCode())
                .as("creating a joke without a session must be 401")
                .isEqualTo(401);
        assertThat(repository.count()).isZero();
    }

    @Test
    void stateChangingRequestWithoutCsrfIsForbidden() throws Exception {
        JokeHttpClient http = authenticatedClient();

        HttpResponse<String> response =
                http.postJsonWithoutCsrf("/api/jokes", "{\"content\":\"Bez tokenu CSRF\"}");

        assertThat(response.statusCode())
                .as("a state-changing request without the CSRF token must be rejected")
                .isEqualTo(403);
        assertThat(repository.count()).isZero();
    }

    @Test
    void persistedJokeIsReadableInAnIndependentTransaction() throws Exception {
        JokeHttpClient http = authenticatedClient();

        HttpResponse<String> created =
                http.postJson("/api/jokes", "{\"content\":\"Trwaly zart\"}");
        assertThat(created.statusCode()).isEqualTo(201);
        long id = ((Number) JsonPath.read(created.body(), "$.id")).longValue();

        // Read the joke back in a brand-new transaction (not the one that wrote it),
        // proving the data was committed to PostgreSQL and survives independently.
        TransactionTemplate independentTransaction = new TransactionTemplate(transactionManager);
        String persistedContent = independentTransaction.execute(status ->
                repository.findById(id).orElseThrow().getContent());

        assertThat(persistedContent).isEqualTo("Trwaly zart");
    }
}
