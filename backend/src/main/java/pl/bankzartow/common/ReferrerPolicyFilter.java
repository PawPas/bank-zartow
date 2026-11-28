package pl.bankzartow.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Adds {@code Referrer-Policy: same-origin} to every response of the
 * application (task T-022, epic E-ML §9 — belt-and-braces against leaking URLs
 * to other origins; the magic-link token itself travels in the URL fragment and
 * never enters a {@code Referer} header anyway). Registered at highest
 * precedence so the header is also present on responses produced inside the
 * security filter chain (401/403), not only on ones that reach MVC.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReferrerPolicyFilter extends OncePerRequestFilter {

    static final String HEADER_NAME = "Referrer-Policy";
    static final String SAME_ORIGIN = "same-origin";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        response.setHeader(HEADER_NAME, SAME_ORIGIN);
        filterChain.doFilter(request, response);
    }
}
