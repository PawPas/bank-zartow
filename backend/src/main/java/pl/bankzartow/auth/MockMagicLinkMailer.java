package pl.bankzartow.auth;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory {@link MagicLinkMailer} for tests and local development (task T-021).
 * Captures the mails it "sends" so tests and the dev preview endpoint can read
 * the link; nothing is ever logged (invariant I5). The outcome of subsequent
 * sends is scriptable, so tests can simulate a definite failure or an uncertain
 * result deterministically.
 *
 * <p>This class is registered as a bean only in test configurations and in the
 * {@code dev} profile ({@link MagicLinkDevConfig}); the production configuration
 * must never contain it (invariant I8, guard-tested).
 */
public final class MockMagicLinkMailer implements MagicLinkMailer {

    /** A mail captured by the mock: who it went to and the confirm link it carried. */
    public record SentMail(String recipient, String link) {
    }

    private final List<SentMail> sentMails = new CopyOnWriteArrayList<>();
    private final AtomicReference<SendOutcome> outcome = new AtomicReference<>(SendOutcome.SENT);

    @Override
    public SendOutcome send(String recipient, String link) {
        SendOutcome result = outcome.get();
        if (result == SendOutcome.SENT) {
            sentMails.add(new SentMail(recipient, link));
        }
        return result;
    }

    /** Scripts the outcome of every subsequent {@link #send} until changed again. */
    public void respondWith(SendOutcome nextOutcome) {
        outcome.set(nextOutcome);
    }

    /** Mails captured so far; only sends that reported {@link SendOutcome#SENT} appear. */
    public List<SentMail> sentMails() {
        return List.copyOf(sentMails);
    }

    /** The most recently captured mail, if any. */
    public Optional<SentMail> lastMail() {
        return sentMails.isEmpty() ? Optional.empty() : Optional.of(sentMails.get(sentMails.size() - 1));
    }

    /** Clears captured mails and restores the {@link SendOutcome#SENT} behaviour. */
    public void reset() {
        sentMails.clear();
        outcome.set(SendOutcome.SENT);
    }
}
