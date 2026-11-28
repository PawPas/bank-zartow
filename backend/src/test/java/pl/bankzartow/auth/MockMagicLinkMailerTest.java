package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import pl.bankzartow.auth.MagicLinkMailer.SendOutcome;

/**
 * Behaviour of the in-memory mock mailer (task T-021): captures exactly the
 * mails that were "sent", and its scripted outcomes drive the failure scenarios
 * of the integration tests deterministically.
 */
class MockMagicLinkMailerTest {

    private final MockMagicLinkMailer mailer = new MockMagicLinkMailer();

    @Test
    void capturesMailOnSuccessfulSend() {
        SendOutcome outcome = mailer.send("owner@example.test", "https://x/login/confirm#token=a");

        assertThat(outcome).isEqualTo(SendOutcome.SENT);
        assertThat(mailer.sentMails()).hasSize(1);
        assertThat(mailer.lastMail()).hasValueSatisfying(mail -> {
            assertThat(mail.recipient()).isEqualTo("owner@example.test");
            assertThat(mail.link()).isEqualTo("https://x/login/confirm#token=a");
        });
    }

    @Test
    void doesNotCaptureMailOnDefiniteFailure() {
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);

        SendOutcome outcome = mailer.send("owner@example.test", "https://x/login/confirm#token=a");

        assertThat(outcome).isEqualTo(SendOutcome.DEFINITE_FAILURE);
        assertThat(mailer.sentMails()).isEmpty();
        assertThat(mailer.lastMail()).isEmpty();
    }

    @Test
    void doesNotCaptureMailOnUncertainOutcome() {
        mailer.respondWith(SendOutcome.UNCERTAIN);

        SendOutcome outcome = mailer.send("owner@example.test", "https://x/login/confirm#token=a");

        assertThat(outcome).isEqualTo(SendOutcome.UNCERTAIN);
        assertThat(mailer.sentMails()).isEmpty();
    }

    @Test
    void scriptedOutcomeStaysUntilChanged() {
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);
        mailer.send("owner@example.test", "link1");
        mailer.send("owner@example.test", "link2");
        mailer.respondWith(SendOutcome.SENT);

        SendOutcome outcome = mailer.send("owner@example.test", "link3");

        assertThat(outcome).isEqualTo(SendOutcome.SENT);
        assertThat(mailer.sentMails()).hasSize(1);
    }

    @Test
    void resetClearsCapturedMailsAndRestoresSending() {
        mailer.send("owner@example.test", "link1");
        mailer.respondWith(SendOutcome.DEFINITE_FAILURE);

        mailer.reset();

        assertThat(mailer.sentMails()).isEmpty();
        assertThat(mailer.send("owner@example.test", "link2")).isEqualTo(SendOutcome.SENT);
        assertThat(mailer.sentMails()).hasSize(1);
    }

    @Test
    void bufferIsBoundedAndOldestEntryIsEvictedWhenFull() {
        for (int i = 0; i <= MockMagicLinkMailer.MAX_CAPTURED; i++) {
            mailer.send("owner@example.test", "https://x/login/confirm#token=t" + i);
        }

        List<MockMagicLinkMailer.SentMail> captured = mailer.sentMails();
        assertThat(captured).hasSize(MockMagicLinkMailer.MAX_CAPTURED);
        assertThat(captured.get(captured.size() - 1).link())
                .as("the last captured entry is the most recently sent mail")
                .endsWith("#token=t" + MockMagicLinkMailer.MAX_CAPTURED);
        assertThat(captured.get(0).link())
                .as("the first (oldest) entry was evicted to make room")
                .doesNotEndWith("#token=t0");
    }

    @Test
    void makeNextSendThrowCausesNextSendToThrow() {
        RuntimeException ex = new RuntimeException("simulated failure");
        mailer.makeNextSendThrow(ex);

        assertThatThrownBy(() -> mailer.send("owner@example.test", "https://x/login/confirm#token=t"))
                .isSameAs(ex);
        assertThat(mailer.sentMails()).as("no mail captured on throw").isEmpty();

        assertThat(mailer.send("owner@example.test", "https://x/login/confirm#token=t"))
                .as("subsequent send works normally after the one-shot throw")
                .isEqualTo(SendOutcome.SENT);
        assertThat(mailer.sentMails()).hasSize(1);
    }
}
