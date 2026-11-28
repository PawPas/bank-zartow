package pl.bankzartow.auth;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin HTTP client for the magic-link request integration tests (task T-021).
 * Keeps a cookie jar and mirrors the SPA: bootstrap the {@code XSRF-TOKEN}
 * cookie via the public session endpoint, then echo it in {@code X-XSRF-TOKEN}
 * on the JSON POST. Deliberately separate from {@code AuthHttpClient} so T-021
 * and T-022 do not edit a shared fixture in parallel.
 */
final class MagicLinkRequestTestClient {

    private final String baseUrl;
    private final CookieManager cookieManager = new CookieManager();
    private final HttpClient client;

    MagicLinkRequestTestClient(int port) {
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

    /** Fetches the public session endpoint so the {@code XSRF-TOKEN} cookie is set. */
    HttpResponse<String> bootstrapCsrf() throws Exception {
        return get("/api/auth/session");
    }

    /** POSTs {@code {"email": ...}} with the CSRF header, like the SPA form does. */
    HttpResponse<String> postMagicLink(String email) throws Exception {
        return postJson("/api/auth/magic-link", emailJson(email), csrfToken());
    }

    /** POSTs the magic-link request deliberately omitting the CSRF header. */
    HttpResponse<String> postMagicLinkWithoutCsrf(String email) throws Exception {
        return postJson("/api/auth/magic-link", emailJson(email), null);
    }

    private String csrfToken() {
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if ("XSRF-TOKEN".equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String emailJson(String email) {
        String escaped = email.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"email\":\"" + escaped + "\"}";
    }

    private HttpResponse<String> postJson(String path, String body, String csrfToken)
            throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (csrfToken != null) {
            builder.header("X-XSRF-TOKEN", csrfToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
