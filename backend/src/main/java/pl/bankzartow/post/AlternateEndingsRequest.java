package pl.bankzartow.post;

import java.util.Objects;

/**
 * Input for generating alternate endings: the joke's introduction and its
 * original punchline. The generator proposes alternatives for this punchline
 * while the original is always preserved by the caller (see Epic 2).
 */
public record AlternateEndingsRequest(String intro, String originalEnding) {

    public AlternateEndingsRequest {
        Objects.requireNonNull(intro, "intro");
        Objects.requireNonNull(originalEnding, "originalEnding");
    }
}
