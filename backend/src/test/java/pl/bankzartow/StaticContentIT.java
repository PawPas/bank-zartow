package pl.bankzartow;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * Verifies that the backend serves the built React frontend: the start page, its
 * hashed assets, and the single-page-app fallback that keeps a reloaded client
 * route from returning 404. Requires the frontend to have been built into the
 * static resources (the Makefile and Dockerfile do this before packaging).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class StaticContentIT {

    private static final Pattern JS_ASSET = Pattern.compile("src=\"(/assets/[^\"]+\\.js)\"");

    @LocalServerPort
    private int port;

    private final HttpClient client =
            HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();

    @Test
    void servesStartPage() throws Exception {
        HttpResponse<String> response = get("/");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type").orElse(""))
                .contains("text/html");
        assertThat(response.body())
                .contains("<div id=\"root\"")
                .contains("Bank żartów");
    }

    @Test
    void servesHashedJsAssetAsJavaScriptNotHtmlFallback() throws Exception {
        String indexHtml = get("/").body();
        Matcher matcher = JS_ASSET.matcher(indexHtml);
        assertThat(matcher.find())
                .as("start page references a built /assets/*.js file")
                .isTrue();

        HttpResponse<String> asset = get(matcher.group(1));

        assertThat(asset.statusCode()).isEqualTo(200);
        // A missing asset would be answered with the index.html SPA fallback (200,
        // text/html). Asserting the JavaScript content type and the absence of the
        // HTML root element makes that fallback fail this test.
        assertThat(asset.headers().firstValue("Content-Type").orElse(""))
                .as("JS asset must be served with a JavaScript content type")
                .containsPattern("(?i)javascript");
        assertThat(asset.body())
                .as("asset body must be the real file, not the index.html fallback")
                .doesNotContain("<div id=\"root\"");
    }

    @Test
    void reloadingClientRouteReturnsAppInsteadOf404() throws Exception {
        HttpResponse<String> response = get("/zarty/42");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("<div id=\"root\"");
    }

    @Test
    void unknownApiPathStillReturns404() throws Exception {
        HttpResponse<String> response = get("/api/nie-istnieje");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("<div id=\"root\"");
    }

    @Test
    void exactApiPathReturns404NotSpaFallback() throws Exception {
        HttpResponse<String> response = get("/api");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).doesNotContain("<div id=\"root\"");
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request =
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
