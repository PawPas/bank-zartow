package pl.bankzartow.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The single account's credentials, supplied from the environment
 * ({@code APP_AUTH_USERNAME} and {@code APP_AUTH_PASSWORD_HASH}). There are no
 * defaults: the canonical constructor rejects a blank username or password hash,
 * so binding fails and the application refuses to start when either is missing.
 * The password is stored only as a bcrypt hash, never in plain text, so the
 * secret never appears in the repository or in logs.
 */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(String username, String passwordHash) {

    public AuthProperties {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("app.auth.username must be set");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("app.auth.password-hash must be set");
        }
    }
}
