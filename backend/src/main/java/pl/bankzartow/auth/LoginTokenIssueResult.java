package pl.bankzartow.auth;

import java.time.Instant;

/**
 * Outcome of {@link LoginTokenService#issue()}: either the freshly generated
 * plain token with its expiry, or "not issued" when the active pool is full
 * (signalled as a value, never as an exception — invariant I4/I6 groundwork).
 *
 * <p>{@link #toString()} deliberately never includes the token, so the secret
 * cannot leak through accidental logging of the result (invariant I5).
 */
public final class LoginTokenIssueResult {

    private static final LoginTokenIssueResult POOL_FULL = new LoginTokenIssueResult(null, null);

    private final String token;
    private final Instant expiresAt;

    private LoginTokenIssueResult(String token, Instant expiresAt) {
        this.token = token;
        this.expiresAt = expiresAt;
    }

    static LoginTokenIssueResult issued(String token, Instant expiresAt) {
        return new LoginTokenIssueResult(token, expiresAt);
    }

    static LoginTokenIssueResult poolFull() {
        return POOL_FULL;
    }

    public boolean isIssued() {
        return token != null;
    }

    /** The plain token, present only right after issuance; it is never persisted or logged. */
    public String token() {
        if (!isIssued()) {
            throw new IllegalStateException("no token was issued (active pool is full)");
        }
        return token;
    }

    public Instant expiresAt() {
        if (!isIssued()) {
            throw new IllegalStateException("no token was issued (active pool is full)");
        }
        return expiresAt;
    }

    @Override
    public String toString() {
        return isIssued()
                ? "LoginTokenIssueResult[issued, expiresAt=" + expiresAt + "]"
                : "LoginTokenIssueResult[poolFull]";
    }
}
