package pl.bankzartow.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Confirmation of a magic-link login (task T-022): an explicit POST exchanges a
 * valid token for exactly one authenticated session — the same servlet session
 * password login uses. Token state lives entirely in {@link LoginTokenService}
 * (T-020); this controller only orchestrates the HTTP side.
 *
 * <p>Sequence and its reasons:
 * <ol>
 * <li><b>Gate first.</b> With {@link MagicLinkGate} closed the endpoint answers
 * {@code 404} as if it did not exist — even when a valid token sits in the
 * database (e.g. production after a provider-configuration rollback) — and the
 * token is not touched.</li>
 * <li><b>Session before consumption.</b> An existing (e.g. anonymous) session
 * gets its id rotated ({@link HttpServletRequest#changeSessionId()},
 * anti-fixation); without one a fresh session is created explicitly — the public
 * {@code GET /api/auth/session} hands out the CSRF cookie but deliberately does
 * not create an {@code HttpSession}, and {@code changeSessionId} alone cannot.
 * Ordering this before consumption guarantees that consuming the token can
 * never be followed by a missing-session failure.</li>
 * <li><b>Atomic consumption + invalidation.</b>
 * {@link LoginTokenService#consumeAndInvalidateOthers} acquires the pool lock
 * and runs both writes in one transaction: exactly one of two racing
 * confirmations of different tokens wins (invariant I3); if invalidation fails
 * the whole transaction rolls back (no half-login). Every refusal yields one
 * byte-for-byte identical generic {@code 401} {@link ProblemDetail} so the
 * cause cannot be distinguished. The plain token is never logged (I5).</li>
 * <li><b>After success</b> the CSRF token is rotated (matching form login
 * behaviour: old cookie cleared, new random value set), then the
 * {@link SecurityContext} of the single account is persisted into the session
 * with Spring Security's session repository, so every subsequent
 * {@code /api/**} request is authenticated.</li>
 * </ol>
 *
 * <p>CSRF is enforced by the dedicated filter chain for this endpoint
 * ({@link MagicLinkConfirmSecurityConfig}); a POST without the proper header is
 * rejected there and never reaches this controller, leaving the token untouched.
 */
@RestController
class MagicLinkConfirmController {

    static final String CONFIRM_PATH = "/api/auth/magic-link/confirm";

    /** Request body. The token travels only here, never in the URL. */
    record ConfirmRequest(String token) {
    }

    private final MagicLinkGate gate;
    private final LoginTokenService tokenService;
    private final AuthProperties authProperties;
    private final SecurityContextHolderStrategy contextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();
    private final CsrfTokenRepository csrfTokenRepository =
            CookieCsrfTokenRepository.withHttpOnlyFalse();

    MagicLinkConfirmController(MagicLinkGate gate, LoginTokenService tokenService,
            AuthProperties authProperties) {
        this.gate = gate;
        this.tokenService = tokenService;
        this.authProperties = authProperties;
    }

    @PostMapping(CONFIRM_PATH)
    ResponseEntity<ProblemDetail> confirm(@RequestBody ConfirmRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        if (!gate.isOpen()) {
            return notFound();
        }
        if (request.getSession(false) != null) {
            request.changeSessionId();
        } else {
            request.getSession(true);
        }
        String token = body.token();
        if (token == null || token.isBlank() || !tokenService.consumeAndInvalidateOthers(token)) {
            return unauthorized();
        }

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                authProperties.username(), null, List.of());
        SecurityContext context = contextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        contextHolderStrategy.setContext(context);
        rotateCsrf(request, response);
        securityContextRepository.saveContext(context, request, response);
        return ResponseEntity.noContent().build();
    }

    /**
     * Rotates the CSRF token after a successful confirmation, matching what
     * form login does via {@code CsrfAuthenticationStrategy}. The old
     * {@code XSRF-TOKEN} cookie is cleared and a new random value is written
     * so the pre-confirmation header value is no longer accepted.
     */
    private void rotateCsrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken existing = csrfTokenRepository.loadToken(request);
        if (existing != null) {
            csrfTokenRepository.saveToken(null, request, response);
            CsrfToken newToken = csrfTokenRepository.generateToken(request);
            csrfTokenRepository.saveToken(newToken, request, response);
        }
    }

    private static ResponseEntity<ProblemDetail> notFound() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Not Found");
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    /**
     * One fixed generic refusal for every cause (unknown, used, expired or
     * invalidated token), mirroring the password login's failure body: no
     * timestamp, path or cause, so all refusals are indistinguishable.
     */
    private static ResponseEntity<ProblemDetail> unauthorized() {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setTitle("Unauthorized");
        problem.setDetail("Uwierzytelnianie nie powiodło się.");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
