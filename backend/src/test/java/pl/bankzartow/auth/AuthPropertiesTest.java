package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Fast unit coverage for the credential guard. Rejecting a blank username or
 * password hash in the constructor is what makes the application refuse to start
 * without login credentials; this verifies it without booting Spring.
 */
class AuthPropertiesTest {

    @Test
    void acceptsNonBlankUsernameAndPasswordHash() {
        assertThatCode(() -> new AuthProperties("tester", "{bcrypt}$2b$10$abc"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsBlankUsername() {
        assertThatThrownBy(() -> new AuthProperties("  ", "{bcrypt}$2b$10$abc"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.auth.username");
    }

    @Test
    void rejectsMissingPasswordHash() {
        assertThatThrownBy(() -> new AuthProperties("tester", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.auth.password-hash");
    }
}
