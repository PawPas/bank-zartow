package pl.bankzartow.auth;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Secures the application for a single account whose credentials come from the
 * environment (see {@link AuthProperties}).
 *
 * <p>Access rules: the healthcheck and every static resource (the single-page
 * app shell and its assets) are public so the login screen can load; everything
 * under {@code /api/**} requires an authenticated session. Unauthenticated API
 * requests get a {@code 401} (via {@link HttpStatusEntryPoint}) rather than a
 * redirect, so the SPA can detect them and show the login screen itself.
 *
 * <p>Login and logout are form-based and keep the session in a cookie. CSRF is
 * enabled for state-changing requests; the token is published in a readable
 * {@code XSRF-TOKEN} cookie and must be echoed in the {@code X-XSRF-TOKEN}
 * header, the standard single-page-app pattern. A failed login returns a generic
 * {@code 401} that does not reveal which field was wrong.
 */
@Configuration
@ConditionalOnWebApplication(type = Type.SERVLET)
public class SecurityConfig {

    /**
     * Fixed, generic RFC 7807 {@link org.springframework.http.ProblemDetail} body for
     * a rejected login, served as {@code application/problem+json} per CONVENTIONS.md.
     * It carries no timestamp, path or field name, so every failure is byte-for-byte
     * identical and never reveals which field was wrong.
     */
    private static final byte[] LOGIN_FAILED_BODY = ("{\"type\":\"about:blank\","
            + "\"title\":\"Unauthorized\",\"status\":401,"
            + "\"detail\":\"Uwierzytelnianie nie powiodło się.\"}").getBytes(StandardCharsets.UTF_8);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/session").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        // Static resources and the single-page-app shell stay public so
                        // the login screen is reachable without a session.
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .addFilterAfter(new CsrfCookieFilter(),
                        org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .failureHandler((request, response, exception) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                            response.getOutputStream().write(LOGIN_FAILED_BODY);
                        }))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpStatus.NO_CONTENT.value()))
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID"))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AuthProperties properties) {
        UserDetails user = User.withUsername(properties.username())
                .password(ensureEncodingPrefix(properties.passwordHash()))
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    /**
     * Accepts a raw bcrypt hash (for example {@code $2b$...}) from the environment.
     * The delegating encoder expects an explicit {@code {id}} prefix, so a bare
     * bcrypt hash is tagged as {@code {bcrypt}} while an already-prefixed hash is
     * left untouched.
     */
    private static String ensureEncodingPrefix(String hash) {
        if (hash.startsWith("{")) {
            return hash;
        }
        return "{bcrypt}" + hash;
    }
}
