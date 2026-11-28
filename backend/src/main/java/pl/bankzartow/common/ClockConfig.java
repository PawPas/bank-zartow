package pl.bankzartow.common;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The single injectable time source (introduced in T-020). Token TTL/expiry and,
 * later, rate-limit windows (T-024) must read time only through this bean, so
 * tests can replace it and move time deterministically instead of sleeping.
 * UTC per CONVENTIONS.md.
 */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
