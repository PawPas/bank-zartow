package pl.bankzartow.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Materialises the deferred CSRF token on every request so the {@code XSRF-TOKEN}
 * cookie is written to the response. Without this the cookie is only sent once the
 * token is read elsewhere, and the single-page app would have no token to echo on
 * its first state-changing request (including login).
 */
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            // Reading the token value triggers the repository to set the cookie.
            csrfToken.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
