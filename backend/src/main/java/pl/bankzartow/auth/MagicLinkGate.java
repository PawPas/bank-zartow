package pl.bankzartow.auth;

import java.util.Set;

/**
 * The single feature gate for magic-link login (task T-020,
 * {@code docs/EPIC-MAGIC-LINK.md} §8). Closed by default; open only with the
 * complete set: {@code APP_AUTH_EMAIL}, {@code APP_PUBLIC_URL} and both declared
 * {@link MagicLinkCapability capabilities} ({@code DELIVERY} and {@code LIMITER}).
 * The endpoints of T-021/T-022 must consult only this gate, so production never
 * exposes magic-link login backed by a mock or without a rate limiter
 * (fail-closed), while a missing variable or capability never prevents startup.
 */
public final class MagicLinkGate {

    private final boolean open;

    public MagicLinkGate(MagicLinkProperties properties, Set<MagicLinkCapability> capabilities) {
        this.open = properties.email().isPresent()
                && properties.publicUrl().isPresent()
                && capabilities.contains(MagicLinkCapability.DELIVERY)
                && capabilities.contains(MagicLinkCapability.LIMITER);
    }

    /** Whether the magic-link endpoints may be active. */
    public boolean isOpen() {
        return open;
    }
}
