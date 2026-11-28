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
 * Thin HTTP client for the magic-link confirmation tests (task T-022). Like
 * {@link AuthHttpClient} it keeps a cookie jar and mirrors the single-page app:
 * bootstrap the {@code XSRF-TOKEN} cookie via the public session endpoint, then
 * echo it in the {@code X-XSRF-TOKEN} header on state-changing requests. It is a
 * separate class on purpose — T-021 evolves {@link AuthHttpClient} in parallel,
 * so the confirmation tests do not touch that shared file.
 */
final class MagicLinkConfirmHttpClient {

    static final String CONFIRM_PATH = "/api/auth/magic-link/confirm";

    private final String baseUrl;
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient client;

    MagicLinkConfirmHttpClient(int port) {
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

    /** POSTs the confirmation JSON with the CSRF header, like the SPA does. */
    HttpResponse<String> confirm(String token) throws Exception {
        return postConfirm(token, csrfToken());
    }

    /** POSTs the confirmation JSON deliberately omitting the CSRF header. */
    HttpResponse<String> confirmWithoutCsrf(String token) throws Exception {
        return postConfirm(token, null);
    }

    /** POSTs the confirmation JSON with an explicitly supplied CSRF header value. */
    HttpResponse<String> confirmWithExplicitCsrf(String token, String csrf) throws Exception {
        return postConfirm(token, csrf);
    }

    /** POSTs the form login with the CSRF header, like the SPA does. */
    HttpResponse<String> login(String username, String password) throws Exception {
        String body = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + "/api/auth/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        String token = csrfToken();
        if (token != null) {
            builder.header("X-XSRF-TOKEN", token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
     * Sends a GET with an explicit {@code JSESSIONID} cookie from a fresh client
     * without a cookie jar, replaying a captured session id directly. A 401 then
     * proves the server itself no longer accepts that session (rotation on
     * confirmation, invalidation on logout) rather than merely that the client
     * dropped the cookie.
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

    private HttpResponse<String> postConfirm(String token, String csrfToken) throws Exception {
        // Tokens are base64url (no quotes or backslashes), so plain interpolation
        // produces valid JSON for every value used in the tests.
        String body = "{\"token\":\"" + token + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + CONFIRM_PATH))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
