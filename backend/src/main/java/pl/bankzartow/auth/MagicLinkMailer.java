package pl.bankzartow.auth;

/**
 * Port for delivering the magic-link mail (task T-021). The only production
 * implementation arrives with the real provider adapter (T-025); test and dev
 * configurations register {@link MockMagicLinkMailer}. No bean of this type
 * exists in the production configuration until then, which is exactly what keeps
 * {@link MagicLinkGate} closed there (invariant I8).
 *
 * <p>Contract (invariant I5, policy D-ML6 — {@code docs/EPIC-MAGIC-LINK.md} §6, §9):
 * <ul>
 *   <li>Implementations must never log or persist the recipient, the token or the
 *       link; the secret may exist only inside the mail itself.</li>
 *   <li>{@link SendOutcome#DEFINITE_FAILURE} is reserved for a synchronous,
 *       unambiguous refusal — the mail certainly did not leave. Anything less
 *       certain (timeout, unknown provider state) is {@link SendOutcome#UNCERTAIN}.</li>
 *   <li>Outcomes are signalled by the return value, never by exceptions; a thrown
 *       {@link RuntimeException} is treated by the caller as an uncertain outcome,
 *       because the mail may already have left the process.</li>
 * </ul>
 */
public interface MagicLinkMailer {

    /** Result of a send attempt, driving the token's fate (policy D-ML6). */
    enum SendOutcome {

        /** The mail was handed over successfully (no delivery guarantee implied). */
        SENT,

        /** Synchronous, unambiguous refusal — the mail certainly was not sent. */
        DEFINITE_FAILURE,

        /** The result is unknown — the mail may or may not have been sent. */
        UNCERTAIN
    }

    /** Attempts to deliver the magic-link mail with the given confirm link. */
    SendOutcome send(String recipient, String link);
}
