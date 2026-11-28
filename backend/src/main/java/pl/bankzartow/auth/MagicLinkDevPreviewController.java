package pl.bankzartow.auth;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Dev-only preview of the last magic link captured by the mock mailer (task
 * T-021): {@code GET /api/auth/magic-link/dev-preview}. This is the single,
 * narrow and explicit exception to the ban on tokens outside the mail
 * (invariant I5) and it exists exclusively in the {@code dev} profile — the
 * production configuration has no such bean, so the path simply yields
 * {@code 404} there (invariant I8, guard-tested). Answers {@code 404} until the
 * mock has captured a mail.
 */
@RestController
@Profile("dev")
public class MagicLinkDevPreviewController {

    /** The last captured confirm link, for manual local testing. */
    public record DevPreviewLink(String link) {
    }

    private final MockMagicLinkMailer mailer;

    MagicLinkDevPreviewController(MockMagicLinkMailer mailer) {
        this.mailer = mailer;
    }

    @GetMapping("/api/auth/magic-link/dev-preview")
    DevPreviewLink lastLink() {
        return mailer.lastMail()
                .map(mail -> new DevPreviewLink(mail.link()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
