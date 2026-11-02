package pl.bankzartow.auth;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

/**
 * Thin HTTP client for the auth integration tests. It keeps a cookie jar (so the
 * session and CSRF cookies persist across calls) and mirrors how the single-page
 * app talks to the backend: read the {@code XSRF-TOKEN} cookie and echo it in the
 * {@code X-XSRF-TOKEN} header on state-changing requests.
 */
final class AuthHttpClient {

    private final String baseUrl;
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient client;

    AuthHttpClient(int port) {
        this.baseUrl = "http://localhost:" + port;
        this.client = HttpClient.newBuilder()
                .proxy(HttpClient.Builder.NO_PROXY)
                .cookieHandler(cookieManager)
                .build();
    }

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** Performs the public session bootstrap so the {@code XSRF-TOKEN} cookie is set. */
    HttpResponse<String> bootstrapSession() throws Exception {
        return get("/api/auth/session");
    }

    /** POSTs the form login with the CSRF header, like the SPA does. */
    HttpResponse<String> login(String username, String password) throws Exception {
        return postForm("/api/auth/login", username, password, csrfToken());
    }

    /** POSTs the form login deliberately omitting the CSRF header. */
    HttpResponse<String> loginWithoutCsrf(String username, String password) throws Exception {
        return postForm("/api/auth/login", username, password, null);
    }

    HttpResponse<String> logout() throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/logout"))
                .POST(HttpRequest.BodyPublishers.noBody());
        String token = csrfToken();
        if (token != null) {
            builder.header("X-XSRF-TOKEN", token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    String csrfToken() {
        return cookieValue("XSRF-TOKEN");
    }

    /** The current session id held in the cookie jar, or {@code null} if none. */
    String sessionId() {
        return cookieValue("JSESSIONID");
    }

    private String cookieValue(String name) {
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * Sends a GET with an explicit {@code JSESSIONID} cookie from a fresh client that
     * has no cookie jar of its own. This replays a captured session id directly,
     * bypassing any client-side cookie deletion, so a 401 proves the server itself
     * invalidated the session rather than merely that the cookie was dropped.
     */
    static HttpResponse<String> getReplayingSession(int port, String path, String sessionId)
            throws Exception {
        HttpClient independent = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Cookie", "JSESSIONID=" + sessionId)
                .GET()
                .build();
        return independent.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> postForm(String path, String username, String password, String csrfToken)
            throws Exception {
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
