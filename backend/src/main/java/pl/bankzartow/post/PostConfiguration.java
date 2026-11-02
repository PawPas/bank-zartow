package pl.bankzartow.post;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the alternate-endings core. The mock is registered as the default
 * {@link AlternateEndingsGenerator} via {@link ConditionalOnMissingBean}, so it
 * is the source in every profile today (tests and local included) while a future
 * real adapter (T-012) can override it by contributing its own bean. No provider
 * is switched automatically at runtime.
 */
@Configuration
@EnableConfigurationProperties(AlternateEndingsProperties.class)
public class PostConfiguration {

    @Bean
    @ConditionalOnMissingBean(AlternateEndingsGenerator.class)
    AlternateEndingsGenerator mockAlternateEndingsGenerator(AlternateEndingsProperties properties) {
        return new MockAlternateEndingsGenerator(properties.mockCount(), properties.mockInterval());
    }

    @Bean
    AlternateEndingsService alternateEndingsService(AlternateEndingsGenerator generator,
                                                    AlternateEndingsProperties properties) {
        return new AlternateEndingsService(generator, properties.maxCount(),
                properties.maxLength(), properties.timeout());
    }
}
