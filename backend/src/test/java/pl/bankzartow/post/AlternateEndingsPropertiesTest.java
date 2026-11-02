package pl.bankzartow.post;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AlternateEndingsPropertiesTest {

    @Test
    void rejectsNonPositiveCountLimit() {
        assertThatThrownBy(() -> new AlternateEndingsProperties(
                0, 280, Duration.ofSeconds(10), 3, Duration.ofMillis(150)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveTimeout() {
        assertThatThrownBy(() -> new AlternateEndingsProperties(
                5, 280, Duration.ZERO, 3, Duration.ofMillis(150)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
