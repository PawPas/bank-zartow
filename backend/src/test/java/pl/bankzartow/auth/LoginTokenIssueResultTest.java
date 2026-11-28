package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * The issue result carries the plain token to the caller but must never expose
 * it through {@code toString()}, so accidental logging of the result cannot leak
 * the secret (invariant I5).
 */
class LoginTokenIssueResultTest {

    @Test
    void issuedResultExposesTokenAndExpiry() {
        Instant expiresAt = Instant.parse("2026-11-28T12:15:00Z");
        LoginTokenIssueResult result = LoginTokenIssueResult.issued("secret-token-value", expiresAt);

        assertThat(result.isIssued()).isTrue();
        assertThat(result.token()).isEqualTo("secret-token-value");
        assertThat(result.expiresAt()).isEqualTo(expiresAt);
    }

    @Test
    void toStringNeverContainsTheToken() {
        LoginTokenIssueResult result = LoginTokenIssueResult.issued(
                "secret-token-value", Instant.parse("2026-11-28T12:15:00Z"));

        assertThat(result.toString()).doesNotContain("secret-token-value");
    }

    @Test
    void poolFullResultHasNoToken() {
        LoginTokenIssueResult result = LoginTokenIssueResult.poolFull();

        assertThat(result.isIssued()).isFalse();
        assertThatThrownBy(result::token).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(result::expiresAt).isInstanceOf(IllegalStateException.class);
    }
}
