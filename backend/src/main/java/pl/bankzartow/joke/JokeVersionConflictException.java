package pl.bankzartow.joke;

/**
 * Thrown when a modifying request carries a version that no longer matches the
 * stored joke, i.e. someone else changed it in the meantime. Mapped to 409 so
 * concurrent edits never overwrite each other silently.
 */
class JokeVersionConflictException extends RuntimeException {

    JokeVersionConflictException(long id, long expectedVersion, long actualVersion) {
        super("joke " + id + " is at version " + actualVersion
                + ", request expected version " + expectedVersion);
    }
}
