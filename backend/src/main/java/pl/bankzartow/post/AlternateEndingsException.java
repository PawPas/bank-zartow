package pl.bankzartow.post;

import java.time.Duration;

/**
 * Domain errors that end an alternate-endings stream. Every failure the core
 * surfaces is one of these types, so a downstream consumer (a future HTTP/SSE
 * endpoint) can map them deterministically. The hierarchy is sealed: the fixed
 * set of failure modes is part of the contract.
 *
 * <p>None of these carry a fallback: a failure terminates the stream, it never
 * silently switches to another (paid) provider.
 */
public abstract sealed class AlternateEndingsException extends RuntimeException {

    protected AlternateEndingsException(String message) {
        super(message);
    }

    protected AlternateEndingsException(String message, Throwable cause) {
        super(message, cause);
    }

    /** The source produced more proposals than the configured maximum. */
    public static final class LimitExceeded extends AlternateEndingsException {

        private final int maxCount;

        public LimitExceeded(int maxCount) {
            super("Alternate endings source produced more than the allowed "
                    + maxCount + " proposals");
            this.maxCount = maxCount;
        }

        public int maxCount() {
            return maxCount;
        }
    }

    /** A single proposal was longer than the configured maximum length. */
    public static final class ProposalTooLong extends AlternateEndingsException {

        private final int length;
        private final int maxLength;

        public ProposalTooLong(int length, int maxLength) {
            super("Alternate ending proposal length " + length
                    + " exceeds the allowed " + maxLength + " characters");
            this.length = length;
            this.maxLength = maxLength;
        }

        public int length() {
            return length;
        }

        public int maxLength() {
            return maxLength;
        }
    }

    /** Generation did not finish within the configured total timeout. */
    public static final class Timeout extends AlternateEndingsException {

        private final Duration timeout;

        public Timeout(Duration timeout) {
            super("Alternate endings generation exceeded the total timeout of " + timeout);
            this.timeout = timeout;
        }

        public Duration timeout() {
            return timeout;
        }
    }

    /** The underlying source failed; mapped to this domain error with no fallback. */
    public static final class SourceFailed extends AlternateEndingsException {

        public SourceFailed(Throwable cause) {
            super("Alternate endings source failed", cause);
        }
    }
}
