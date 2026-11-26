package pl.bankzartow.joke;

/** Thrown when a joke with the requested id does not exist. Mapped to 404. */
class JokeNotFoundException extends RuntimeException {

    JokeNotFoundException(long id) {
        super("joke " + id + " does not exist");
    }
}
