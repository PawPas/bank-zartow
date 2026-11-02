package pl.bankzartow.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Ephemeral single-account credentials for integration tests. The username and
 * password are throwaway fixtures (not real secrets) and the bcrypt hash is
 * computed at runtime, so no secret is ever committed. The plain password is only
 * used by tests that perform a real login; it is never written to logs.
 */
public final class AuthTestFixtures {

    public static final String USERNAME = "tester";
    public static final String PASSWORD = "ephemeral-test-password";
    public static final String PASSWORD_HASH = new BCryptPasswordEncoder().encode(PASSWORD);

    private AuthTestFixtures() {
    }

    /** Registers the fixture credentials so a full application context can start. */
    public static void registerCredentials(DynamicPropertyRegistry registry) {
        registry.add("app.auth.username", () -> USERNAME);
        registry.add("app.auth.password-hash", () -> PASSWORD_HASH);
    }
}
