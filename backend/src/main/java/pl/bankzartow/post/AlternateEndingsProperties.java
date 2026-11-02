package pl.bankzartow.post;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables for the alternate-endings core. All values have safe defaults so the
 * feature works with no configuration and no secrets. The mock settings
 * ({@code mockCount}, {@code mockInterval}) describe the default source; the
 * limit settings ({@code maxCount}, {@code maxLength}, {@code timeout}) are the
 * provider-neutral rules enforced by {@link AlternateEndingsService}.
 */
@ConfigurationProperties(prefix = "app.post.alternate-endings")
public record AlternateEndingsProperties(
        @DefaultValue("5") int maxCount,
        @DefaultValue("280") int maxLength,
        @DefaultValue("10s") Duration timeout,
        @DefaultValue("3") int mockCount,
        @DefaultValue("150ms") Duration mockInterval) {

    public AlternateEndingsProperties {
        requirePositive(maxCount, "max-count");
        requirePositive(maxLength, "max-length");
        requirePositive(mockCount, "mock-count");
        requirePositiveDuration(timeout, "timeout");
        requireNonNegativeDuration(mockInterval, "mock-interval");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(
                    "app.post.alternate-endings." + name + " must be positive, was " + value);
        }
    }

    private static void requirePositiveDuration(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    "app.post.alternate-endings." + name + " must be a positive duration, was " + value);
        }
    }

    private static void requireNonNegativeDuration(Duration value, String name) {
        if (value == null || value.isNegative()) {
            throw new IllegalArgumentException(
                    "app.post.alternate-endings." + name + " must not be negative, was " + value);
        }
    }
}
