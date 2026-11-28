package pl.bankzartow.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Narrow, dedicated filter chain for the single public magic-link confirmation
 * endpoint (task T-022). It exists as a separate chain so the main
 * {@link SecurityConfig} — evolved in parallel by T-021 — stays untouched: this
 * chain matches exactly {@code POST /api/auth/magic-link/confirm} and, with
 * {@code @Order(0)}, is consulted before the main chain (which carries no
 * {@code @Order}, i.e. lowest precedence, and keeps handling every other
 * request, including {@code /api/**} → authenticated).
 *
 * <p>The endpoint is public (a user confirming from a new browser has no
 * session), but keeps the exact CSRF configuration of the main chain: the
 * double-submit {@code XSRF-TOKEN} cookie with the {@code X-XSRF-TOKEN} header,
 * plus {@link CsrfCookieFilter} to materialise the cookie. A POST without the
 * proper header is rejected with {@code 403} before the controller runs, so the
 * token is not consumed. Session management and security-context persistence
 * use the same defaults as the main chain; the session itself is established
 * explicitly in {@link MagicLinkConfirmController}.
 *
 * <p>Deliberate duplication note: the CSRF wiring repeats a few lines of
 * {@link SecurityConfig}. Folding both into one shared method would modify that
 * file, which T-021 owns right now; unifying them is a trivial follow-up once
 * both tasks are merged.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class MagicLinkConfirmSecurityConfig {

    @Bean
    @Order(0)
    SecurityFilterChain magicLinkConfirmFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(PathPatternRequestMatcher.withDefaults()
                        .matcher(HttpMethod.POST, MagicLinkConfirmController.CONFIRM_PATH))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .addFilterAfter(new CsrfCookieFilter(), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

}
