package org.institutonata.suprihub.shared.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.institutonata.suprihub.shared.config.SupriHubProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private final SupriHubProperties props = new SupriHubProperties("http://localhost", "America/Sao_Paulo",
            null, null, null, new SupriHubProperties.RateLimit(true, 3, 2), null, null, null, null, null);

    @Test
    void bloqueiaAposOLimiteNoLogin() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props);
        assertThat(call(filter, "/api/auth/login", "10.0.0.1")).isEqualTo(200);
        assertThat(call(filter, "/api/auth/login", "10.0.0.1")).isEqualTo(200);
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilter(request("/api/auth/login", "10.0.0.1"), blocked, new MockFilterChain());
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getHeader("Retry-After")).isNotBlank();
        assertThat(blocked.getContentAsString()).contains("Muitas tentativas");
        // outro IP não é afetado
        assertThat(call(filter, "/api/auth/login", "10.0.0.2")).isEqualTo(200);
    }

    @Test
    void naoLimitaEndpointsAutenticados() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(props);
        for (int i = 0; i < 10; i++) {
            assertThat(call(filter, "/api/me", "10.0.0.3")).isEqualTo(200);
        }
    }

    private int call(RateLimitFilter filter, String uri, String ip) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(uri, ip), response, new MockFilterChain());
        return response.getStatus();
    }

    private MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", uri);
        req.setRemoteAddr(ip);
        return req;
    }
}
