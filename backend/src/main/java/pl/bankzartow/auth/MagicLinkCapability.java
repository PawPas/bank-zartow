package pl.bankzartow.auth;

/**
 * Abstract capability contributions required by {@link MagicLinkGate} (task T-020,
 * {@code docs/EPIC-MAGIC-LINK.md} §8). A capability is declared by registering the
 * enum constant as a Spring bean. This type deliberately knows nothing about the
 * mailer port (T-021) or the rate limiter (T-024).
 *
 * <p>In the production configuration nothing in this task contributes either
 * capability, so the gate stays closed until the real mail adapter (T-025,
 * "wysylka") and the rate limiter (T-024, "limiter") declare them. Test and dev
 * configurations declare them explicitly.
 */
public enum MagicLinkCapability {

    /** Real mail delivery is available ("wysylka" in the epic design). */
    DELIVERY,

    /** Rate limiting is in place ("limiter" in the epic design). */
    LIMITER
}
