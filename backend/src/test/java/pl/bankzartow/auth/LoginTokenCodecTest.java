package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.Base64;
import org.junit.jupiter.api.Test;

/**
 * Token generation and hashing (acceptance criteria I1 groundwork): 256 bits
 * drawn from the provided {@link SecureRandom}, base64url without padding, and
 * a lowercase SHA-256 hex digest as the only persisted form.
 */
class LoginTokenCodecTest {

    /** A SecureRandom stub yielding a known byte sequence, to prove the codec uses it. */
    private static final class FixedRandom extends SecureRandom {
        @Override
        public void nextBytes(byte[] bytes) {
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) i;
            }
        }
    }

    @Test
    void generates43CharacterUrlSafeTokenWithoutPadding() {
        String token = LoginTokenCodec.generate(new SecureRandom());

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]+");
    }

    @Test
    void draws256BitsFromTheProvidedSecureRandom() {
        String token = LoginTokenCodec.generate(new FixedRandom());

        byte[] decoded = Base64.getUrlDecoder().decode(token);
        assertThat(decoded).hasSize(LoginTokenCodec.TOKEN_BYTES);
        for (int i = 0; i < decoded.length; i++) {
            assertThat(decoded[i]).isEqualTo((byte) i);
        }
    }

    @Test
    void hashesWithSha256ToLowercaseHex() {
        // Well-known SHA-256 test vector for the ASCII string "abc".
        assertThat(LoginTokenCodec.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    void hashNeverEqualsThePlainToken() {
        String token = LoginTokenCodec.generate(new SecureRandom());

        assertThat(LoginTokenCodec.sha256Hex(token))
                .hasSize(64)
                .matches("[0-9a-f]+")
                .isNotEqualTo(token);
    }
}
