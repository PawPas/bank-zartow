package pl.bankzartow.joke;

/**
 * Thrown when the {@code If-Match} header of a delete request does not carry a
 * parseable version number. Mapped to 400 (a missing header is already turned
 * into 400 by Spring itself).
 */
class InvalidVersionHeaderException extends RuntimeException {

    InvalidVersionHeaderException(String value) {
        super("If-Match header is not a version number: " + value);
    }
}
