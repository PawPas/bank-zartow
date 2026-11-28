package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The gate must be closed by default and open only with the complete set of
 * variables and capabilities; every single missing element keeps it closed
 * (acceptance criterion of T-020, {@code docs/EPIC-MAGIC-LINK.md} §8). All
 * combinations are pure and deterministic — no environment, no context.
 */
class MagicLinkGateTest {

    private static final Set<MagicLinkCapability> NO_CAPABILITIES =
            EnumSet.noneOf(MagicLinkCapability.class);
    private static final Set<MagicLinkCapability> ALL_CAPABILITIES =
            EnumSet.allOf(MagicLinkCapability.class);

    private static MagicLinkProperties properties(String email, String publicUrl) {
        return new MagicLinkProperties(Optional.ofNullable(email), Optional.ofNullable(publicUrl),
                Duration.ofMinutes(15), 3);
    }

    @Test
    void isClosedByDefaultWithoutVariablesAndCapabilities() {
        MagicLinkGate gate = new MagicLinkGate(properties(null, null), NO_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithoutEmail() {
        MagicLinkGate gate = new MagicLinkGate(
                properties(null, "https://example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithoutPublicUrl() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", null), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithoutDeliveryCapability() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test"),
                EnumSet.of(MagicLinkCapability.LIMITER));

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithoutLimiterCapability() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test"),
                EnumSet.of(MagicLinkCapability.DELIVERY));

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedInProductionLikeSetupWithVariablesButNoCapabilities() {
        // Production before T-024/T-025: both variables set, nothing contributes
        // capabilities — the feature must remain off (fail-closed).
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test"), NO_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithCapabilitiesButNoVariables() {
        MagicLinkGate gate = new MagicLinkGate(properties(null, null), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void opensWithCompleteVariablesAndExplicitCapabilities() {
        // Test/dev setup: both variables plus explicitly declared capabilities.
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void opensWithHttpPublicUrl() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "http://localhost:8080"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    void staysClosedWithPublicUrlHavingNonRootPath() {
        // Only empty path or "/" is accepted; a sub-path breaks the fixed /login/confirm contract.
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test/app"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithSingleSlashHttpsUrl() {
        // https:/host has no authority component — uri.getHost() is null.
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https:/bank.example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithOpaqueHttpsUrl() {
        // https:host is an opaque URI — uri.getHost() is null.
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https:bank.example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithPublicUrlContainingQueryString() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test?x=1"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithPublicUrlContainingFragment() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://example.test#frag"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithPublicUrlContainingUserInfo() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "https://user:pass@example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithRelativePublicUrl() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "/relative/path"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }

    @Test
    void staysClosedWithNonHttpPublicUrlScheme() {
        MagicLinkGate gate = new MagicLinkGate(
                properties("owner@example.test", "ftp://example.test"), ALL_CAPABILITIES);

        assertThat(gate.isOpen()).isFalse();
    }
}
