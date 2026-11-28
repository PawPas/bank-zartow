package pl.bankzartow.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import pl.bankzartow.MutableClock;
import pl.bankzartow.TestcontainersConfiguration;

/**
 * Verifies the rollback guarantee of {@link LoginTokenService#consumeAndInvalidateOthers}:
 * if the invalidation step throws, the consume UPDATE must be reverted so the token
 * remains intact and no half-login is possible.
 */
@SpringBootTest
@Import({TestcontainersConfiguration.class, ConsumeAndInvalidateRollbackIT.TestClockConfig.class})
class ConsumeAndInvalidateRollbackIT {

    private static final Instant BASE = Instant.parse("2026-11-28T12:00:00Z");

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE);
        }
    }

    @MockitoSpyBean
    private LoginTokenRepository repository;

    @Autowired
    private LoginTokenService service;

    @Autowired
    private JdbcTemplate jdbc;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        AuthTestFixtures.registerCredentials(registry);
        registry.add("app.magic-link.ttl-minutes", () -> "15");
        registry.add("app.magic-link.max-active-tokens", () -> "3");
    }

    @BeforeEach
    void resetDatabase() {
        jdbc.update("delete from login_token");
    }

    @Test
    void rollsBackConsumeWhenInvalidationThrows() {
        String token = service.issue().token();
        doThrow(new RuntimeException("simulated DB failure during invalidation"))
                .when(repository).invalidateAllActive(any(Instant.class));

        assertThatThrownBy(() -> service.consumeAndInvalidateOthers(token))
                .isInstanceOf(RuntimeException.class);

        OffsetDateTime usedAt = jdbc.queryForObject(
                "select used_at from login_token", OffsetDateTime.class);
        assertThat(usedAt)
                .as("transaction rolled back: consume UPDATE is reverted when invalidation throws")
                .isNull();

        // The token remains valid and can still be consumed normally.
        assertThat(service.consume(token)).isTrue();
    }
}
