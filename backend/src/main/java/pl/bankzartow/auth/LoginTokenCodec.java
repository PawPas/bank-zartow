package pl.bankzartow.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Generation and hashing of magic-link tokens. A token is 256 bits from
 * {@link SecureRandom}, base64url-encoded without padding (43 characters). Only
 * its SHA-256 hex digest is ever persisted (invariant I1): a fast hash is correct
 * here because the input already has full cryptographic entropy — bcrypt/argon2
 * protect low-entropy passwords, not random 256-bit tokens — and the lookup is an
 * exact match on the digest.
 */
final class LoginTokenCodec {

    static final int TOKEN_BYTES = 32;

    private LoginTokenCodec() {
    }

    /** New 256-bit token drawn from the given {@link SecureRandom}, base64url without padding. */
    static String generate(SecureRandom random) {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Lowercase SHA-256 hex digest (64 characters) of the plain token. */
    static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM is required to ship SHA-256 (JCA standard algorithms).
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
