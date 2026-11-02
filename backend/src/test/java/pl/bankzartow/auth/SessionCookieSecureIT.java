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
 * Confirms the production hardening: when {@code APP_SESSION_COOKIE_SECURE=true}
 * (as it is on Railway/HTTPS) the session cookie carries the {@code Secure} flag.
 * The flag is driven by the environment, so this behaviour is switched on by the
 * {@code server.servlet.session.cookie.secure} property registered below.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class SessionCookieSecureIT {

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        registry.add("server.servlet.session.cookie.secure", () -> "true");
    }

    @Test
    void sessionCookieIsSecureWhenEnabledForProduction() throws Exception {
        AuthHttpClient http = new AuthHttpClient(port);
        http.bootstrapSession();

        HttpResponse<String> login = http.login(AuthTestFixtures.USERNAME, AuthTestFixtures.PASSWORD);

        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst()
                .orElseThrow(() -> new AssertionError("login did not set a session cookie"));
        assertThat(sessionCookie).containsIgnoringCase("Secure");
    }
}
