package pl.bankzartow.post;

import java.util.Objects;

/**
 * A single alternate ending (punchline) proposal produced by an
 * {@link AlternateEndingsGenerator}. The value is provider-neutral: it carries
 * only the proposal text and no transport or provider detail.
 */
public record AlternateEnding(String text) {

    public AlternateEnding {
        Objects.requireNonNull(text, "text");
    }
}
