package pl.bankzartow.joke;

/**
 * Raised when a joke's content is blank once normalized (for example only
 * whitespace or non-breaking spaces). This catches input that passes a plain
 * {@code @NotBlank} check but collapses to nothing after
 * {@link pl.bankzartow.common.TextNormalizer normalization}. Mapped to a
 * {@code 400} {@code ProblemDetail} by {@link JokeExceptionHandler}.
 */
public class BlankJokeContentException extends RuntimeException {

    public BlankJokeContentException() {
        super("Joke content must not be blank after normalization.");
    }
}
