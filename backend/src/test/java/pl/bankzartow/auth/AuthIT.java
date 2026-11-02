package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Behavioural coverage for the single-account authentication (task T-006).
 * Exercises the real HTTP stack against a Testcontainers PostgreSQL, using
 * ephemeral fixture credentials registered below.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class AuthIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
    }

    @Test
    void apiRequestWithoutSessionReturns401NotRedirect() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);

        HttpResponse<String> response = http.get("/api/auth/me");

        assertThat(response.statusCode())
                .as("protected /api request without a session must be 401, not a 3xx redirect")
                .isEqualTo(401);
    }

    @Test
    void validCredentialsAuthenticateAndReachProtectedApi() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> login = http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);
        assertThat(login.statusCode()).isEqualTo(204);

        HttpResponse<String> me = http.get("/api/auth/me");
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(me.body()).contains("\"username\":\"" + AuthTestFixtures.USERNAME + "\"");
    }

    @Test
    void wrongPasswordReturnsGeneric401WithoutRevealingField() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> login = http.login(AuthTestFixtures.USERNAME, "wrong-password");

        assertThat(login.statusCode()).isEqualTo(401);
        assertThat(login.body().toLowerCase())
                .as("the failure must not reveal which field was wrong")
                .doesNotContain("password")
                .doesNotContain("username")
                .doesNotContain("hasło")
                .doesNotContain("login");
    }

    @Test
    void wrongUsernameReturnsSameGeneric401AsWrongPassword() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> unknownUser = http.login("someone-else", AuthTestFixtures.PASSWORD);
        HttpResponse<String> wrongPassword = http.login(AuthTestFixtures.USERNAME, "wrong-password");

        assertThat(unknownUser.statusCode()).isEqualTo(401);
        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownUser.body())
                .as("an unknown user and a wrong password must be indistinguishable")
                .isEqualTo(wrongPassword.body());
    }

    @Test
    void sessionIsInvalidAfterLogout() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();
        http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);

        HttpResponse<String> logout = http.logout();
        assertThat(logout.statusCode()).isEqualTo(204);

        assertThat(http.get("/api/auth/me").statusCode())
                .as("the session must be invalid after logout")
                .isEqualTo(401);
    }

    @Test
    void stateChangingRequestWithoutCsrfTokenIsForbidden() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> login =
                http.loginWithoutCsrf(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);

        assertThat(login.statusCode())
                .as("a POST without the CSRF token must be rejected")
                .isEqualTo(403);
    }

    @Test
    void loginAgainAfterLogoutSucceedsWhenSpaRefreshesCsrf() throws Exception {
        // Models the SPA flow: a successful login rotates away the XSRF-TOKEN cookie
        // and logout clears it, but the SPA re-fetches the session (a GET) after both,
        // which re-arms the cookie via CsrfCookieFilter. This proves logging in again
        // after logout works without a page reload.
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();
        assertThat(http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD).statusCode())
                .isEqualTo(204);

        http.bootstrapSession(); // SPA's post-login session refresh re-arms the token
        assertThat(http.logout().statusCode()).isEqualTo(204);

        http.bootstrapSession(); // SPA's post-logout session refresh re-arms the token
        assertThat(http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD).statusCode())
                .as("logging in again after logout must work once the SPA refreshes CSRF")
                .isEqualTo(204);
        assertThat(http.get("/api/auth/me").statusCode()).isEqualTo(200);
    }

    @Test
    void unauthenticatedApiPathReturns401NotSpaFallback() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);

        HttpResponse<String> response = http.get("/api/nie-istnieje");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body())
                .as("a protected /api path must not be served the SPA HTML shell")
                .doesNotContain("<div id=\"root\"");
    }

    @Test
    void authenticatedUnknownApiPathReturns404NotSpaFallback() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();
        http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);

        HttpResponse<String> response = http.get("/api/nie-istnieje");

        assertThat(response.statusCode())
                .as("an authenticated miss under /api must be a real 404, not the SPA fallback")
                .isEqualTo(404);
        assertThat(response.body()).doesNotContain("<div id=\"root\"");
    }

    @Test
    void sessionCookieIsHttpOnlyAndSameSite() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> login = http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);

        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("login did not set a session cookie"));
        assertThat(sessionCookie).containsIgnoringCase("HttpOnly");
        assertThat(sessionCookie).containsIgnoringCase("SameSite=Lax");
    }
}
