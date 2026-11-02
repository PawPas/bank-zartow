package pl.bankzartow.auth;

import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal authentication state for the single-page app.
 *
 * <p>{@code GET /api/auth/session} is public: the SPA calls it on load both to
 * learn whether a session already exists and to receive the {@code XSRF-TOKEN}
 * cookie it must echo on login. {@code GET /api/auth/me} is protected and is used
 * by tests (and the SPA) as a representative {@code /api/**} resource that returns
 * {@code 401} without a session.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Authentication state. {@code username} is {@code null} when anonymous. */
    public record SessionInfo(boolean authenticated, String username) {
    }

    @GetMapping("/session")
    public SessionInfo session(Principal principal) {
        if (principal == null) {
            return new SessionInfo(false, null);
        }
        return new SessionInfo(true, principal.getName());
    }

    @GetMapping("/me")
    public SessionInfo me(Principal principal) {
        return new SessionInfo(true, principal.getName());
    }
}
