package pl.bankzartow.auth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
 *
 * <p>The captured-mail buffer is bounded at {@value #MAX_CAPTURED} entries: once
 * full, the oldest entry is dropped on each new capture so a long-running dev
 * server does not accumulate unbounded state. Tests call {@link #reset()} between
 * runs and are therefore unaffected by the cap.
 */
public final class MockMagicLinkMailer implements MagicLinkMailer {

    public static final int MAX_CAPTURED = 50;

    /** A mail captured by the mock: who it went to and the confirm link it carried. */
    public record SentMail(String recipient, String link) {

        @Override
        public String toString() {
            return "SentMail[REDACTED]";
        }
    }

    private final List<SentMail> sentMails = new ArrayList<>();
    private final AtomicReference<SendOutcome> outcome = new AtomicReference<>(SendOutcome.SENT);
    private final AtomicReference<RuntimeException> nextThrow = new AtomicReference<>();

    @Override
    public synchronized SendOutcome send(String recipient, String link) {
        RuntimeException toThrow = nextThrow.getAndSet(null);
        if (toThrow != null) throw toThrow;
        SendOutcome result = outcome.get();
        if (result == SendOutcome.SENT) {
            if (sentMails.size() >= MAX_CAPTURED) {
                sentMails.remove(0);
            }
            sentMails.add(new SentMail(recipient, link));
        }
        return result;
    }

    /** Scripts the outcome of every subsequent {@link #send} until changed again. */
    public void respondWith(SendOutcome nextOutcome) {
        outcome.set(nextOutcome);
    }

    /**
     * Scripts the next {@link #send} call to throw {@code ex} instead of
     * returning an outcome. The exception is thrown once and then cleared; a
     * RuntimeException carrying the recipient in its message lets tests verify
     * that the service never leaks the message to the logs (invariant I5).
     */
    public void makeNextSendThrow(RuntimeException ex) {
        nextThrow.set(ex);
    }

    /**
     * Recent mails captured (at most {@value #MAX_CAPTURED}); only sends that
     * reported {@link SendOutcome#SENT} appear.
     */
    public synchronized List<SentMail> sentMails() {
        return List.copyOf(sentMails);
    }

    /** The most recently captured mail, if any. */
    public synchronized Optional<SentMail> lastMail() {
        return sentMails.isEmpty() ? Optional.empty() : Optional.of(sentMails.get(sentMails.size() - 1));
    }

    /** Clears captured mails, restores {@link SendOutcome#SENT}, and cancels any pending throw. */
    public synchronized void reset() {
        sentMails.clear();
        outcome.set(SendOutcome.SENT);
        nextThrow.set(null);
    }
}
