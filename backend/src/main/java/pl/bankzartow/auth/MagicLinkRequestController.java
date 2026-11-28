package pl.bankzartow.auth;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public endpoint requesting a magic link (task T-021): {@code POST
 * /api/auth/magic-link}. Active only while {@link MagicLinkGate} is open —
 * otherwise it answers {@code 404} as if it did not exist, and the rest of the
 * application is untouched. CSRF protection applies like to every other
 * state-changing request (double-submit cookie).
 *
 * <p>The gate is consulted before validation, so a closed gate answers {@code 404}
 * even to a semantically invalid address; only a transport-level JSON parse error
 * can yield {@code 400} ahead of the gate. With the gate open, a syntactically
 * invalid request is the only {@code 400}; every processed request — allowed or
 * foreign address, send failure, full pool — returns the identical empty
 * {@code 202} (invariant I6).
 */
@RestController
class MagicLinkRequestController {

    /**
     * Request body. The compact constructor trims the address so format
     * validation tolerates surrounding whitespace; full normalization (trim +
     * lower case) happens in {@link MagicLinkRequestService}.
     */
    record MagicLinkRequest(@NotBlank @Email String email) {
        MagicLinkRequest {
            email = email == null ? null : email.trim();
        }

        @Override
        public String toString() {
            return "MagicLinkRequest[REDACTED]";
        }
    }

    private final MagicLinkGate gate;
    private final MagicLinkRequestService service;
    private final Validator validator;

    MagicLinkRequestController(MagicLinkGate gate, MagicLinkRequestService service,
            Validator validator) {
        this.gate = gate;
        this.service = service;
        this.validator = validator;
    }

    @PostMapping("/api/auth/magic-link")
    ResponseEntity<Void> request(@RequestBody MagicLinkRequest body) {
        if (!gate.isOpen()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        // Validated programmatically (not via @Valid) so the closed gate above
        // wins over format validation and answers 404 first. The detail stays
        // generic and never echoes the submitted address.
        if (!validator.validate(body).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request content.");
        }
        service.requestMagicLink(body.email());
        return ResponseEntity.accepted().build();
    }
}
