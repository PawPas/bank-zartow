package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Binding rules of the magic-link configuration: everything optional (a missing
 * value keeps the gate closed instead of breaking startup), e-mail normalized by
 * trim + lower case, and the reversible defaults TTL 15 min (D-ML4) and pool
 * size 3 (D-ML6) applied in code.
 */
class MagicLinkPropertiesTest {

    @Test
    void appliesReversibleDefaultsWhenNothingIsConfigured() {
        MagicLinkProperties properties = MagicLinkProperties.from(null, null, null, null);

        assertThat(properties.email()).isEmpty();
        assertThat(properties.publicUrl()).isEmpty();
        assertThat(properties.tokenTtl()).isEqualTo(Duration.ofMinutes(15));
        assertThat(properties.maxActiveTokens()).isEqualTo(3);
    }

    @Test
    void normalizesEmailWithTrimAndLowerCase() {
        MagicLinkProperties properties =
                MagicLinkProperties.from("  Owner@Example.TEST  ", null, null, null);

        assertThat(properties.email()).contains("owner@example.test");
    }

    @Test
    void treatsBlankValuesAsAbsent() {
        MagicLinkProperties properties = MagicLinkProperties.from("   ", "  ", null, null);

        assertThat(properties.email()).isEmpty();
        assertThat(properties.publicUrl()).isEmpty();
    }

    @Test
    void trimsPublicUrl() {
        MagicLinkProperties properties =
                MagicLinkProperties.from(null, " https://bank.example.test ", null, null);

        assertThat(properties.publicUrl()).contains("https://bank.example.test");
    }

    @Test
    void honoursConfiguredTtlAndPoolSize() {
        MagicLinkProperties properties = MagicLinkProperties.from(null, null, 5L, 7);

        assertThat(properties.tokenTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(properties.maxActiveTokens()).isEqualTo(7);
    }

    @Test
    void rejectsNonPositiveTtl() {
        assertThatThrownBy(() -> MagicLinkProperties.from(null, null, 0L, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl-minutes");
    }

    @Test
    void rejectsNonPositivePoolSize() {
        assertThatThrownBy(() -> MagicLinkProperties.from(null, null, null, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-active-tokens");
    }
}
