package pl.bankzartow.joke;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client for the joke integration tests. Keeps a cookie jar so the
 * session and CSRF cookies persist across calls, and mirrors how the single-page
 * app talks to the backend: read the {@code XSRF-TOKEN} cookie and echo it in the
 * {@code X-XSRF-TOKEN} header on state-changing requests. It exercises the real
 * security stack rather than bypassing it with mock authentication.
 */
final class JokeHttpClient {

    private final String baseUrl;
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient client;

    JokeHttpClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .cookieHandler(cookieManager)
                .build();
    }

    /** Performs the full SPA login flow so later requests carry a valid session. */
    void login(String username, String password) throws Exception {
        get("/api/auth/session"); // public bootstrap: sets the XSRF-TOKEN cookie
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-XSRF-TOKEN", csrfToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204) {
            throw new IllegalStateException("test login failed with status " + response.statusCode());
        }
        get("/api/auth/session"); // re-arm the CSRF cookie the login rotated away
    }

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** POSTs a JSON body echoing the CSRF token, like the SPA does. */
    HttpResponse<String> postJson(String path, String json) throws Exception {
        return postJson(path, json, csrfToken());
    }

    /** POSTs a JSON body deliberately omitting the CSRF token. */
    HttpResponse<String> postJsonWithoutCsrf(String path, String json) throws Exception {
        return postJson(path, json, null);
    }

    private HttpResponse<String> postJson(String path, String json, String csrfToken) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String csrfToken() {
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
