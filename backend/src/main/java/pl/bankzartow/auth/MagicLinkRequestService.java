package pl.bankzartow.auth;

import java.util.Locale;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Handles a magic-link request (task T-021): normalizes the submitted address,
 * compares it with the single allowed address, and for a match issues a token
 * (T-020 service) and mails the confirm link through the {@link MagicLinkMailer}
 * port. The caller observes nothing about the outcome — the HTTP response is
 * always the same {@code 202} (invariant I6); this service returns no result and
 * logs nothing (invariant I5).
 *
 * <p>Token fate after a send attempt (policy D-ML6, {@code docs/EPIC-MAGIC-LINK.md}
 * §6): a definite failure immediately invalidates the fresh token so failed sends
 * never fill the active pool; an uncertain outcome (including a thrown exception —
 * the mail may already have left) keeps the token active until its TTL.
 *
 * <p>The mailer is resolved lazily: the production configuration has no mailer
 * bean until T-025, which also keeps {@link MagicLinkGate} closed there, so this
 * branch is only defensive.
 */
@Service
public class MagicLinkRequestService {

    private final LoginTokenService tokens;
    private final MagicLinkProperties properties;
    private final ObjectProvider<MagicLinkMailer> mailers;

    public MagicLinkRequestService(LoginTokenService tokens, MagicLinkProperties properties,
            ObjectProvider<MagicLinkMailer> mailers) {
        this.tokens = tokens;
        this.properties = properties;
        this.mailers = mailers;
    }

    /**
     * Processes one request for a magic link. Deliberately {@code void}: every
     * path (foreign address, full pool, send failure) is indistinguishable to the
     * caller (invariant I6).
     */
    void requestMagicLink(String submittedEmail) {
        String normalized = normalizeEmail(submittedEmail);
        if (properties.email().filter(normalized::equals).isEmpty()) {
            return;
        }
        MagicLinkMailer mailer = mailers.getIfAvailable();
        if (mailer == null) {
            return;
        }
        LoginTokenIssueResult issued = tokens.issue();
        if (!issued.isIssued()) {
            return;
        }
        String link = confirmLink(properties.publicUrl().orElseThrow(), issued.token());
        MagicLinkMailer.SendOutcome outcome;
        try {
            outcome = mailer.send(normalized, link);
        } catch (RuntimeException e) {
            // The mail may already have left the process, so a thrown exception is
            // an uncertain outcome: the token stays active until TTL (D-ML6).
            // Deliberately not logged: the exception may carry the recipient (I5).
            outcome = MagicLinkMailer.SendOutcome.UNCERTAIN;
        }
        if (outcome == MagicLinkMailer.SendOutcome.DEFINITE_FAILURE) {
            tokens.invalidate(issued.token());
        }
    }

    /** Normalization used for the allowed-address comparison: trim + lower case. */
    static String normalizeEmail(String raw) {
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the mailed confirm link: fixed {@code /login/confirm} path, token in
     * the URL fragment (never in the query), no redirect parameters.
     */
    static String confirmLink(String publicUrl, String token) {
        String base = publicUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/login/confirm#token=" + token;
    }
}
