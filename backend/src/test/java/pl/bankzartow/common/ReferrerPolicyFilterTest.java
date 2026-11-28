package pl.bankzartow.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ReferrerPolicyFilterTest {

    @Test
    void setsSameOriginReferrerPolicyAndContinuesTheChain() throws Exception {
        ReferrerPolicyFilter filter = new ReferrerPolicyFilter();
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/session");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Referrer-Policy")).isEqualTo("same-origin");
        assertThat(chain.getRequest())
                .as("the filter only decorates the response and passes the request on")
                .isSameAs(request);
    }
}
