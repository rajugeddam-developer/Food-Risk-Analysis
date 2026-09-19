package com.foodrisk.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new RateLimitingFilter(5, 10); // Lower limits for quick unit testing
        filterChain = mock(FilterChain.class);
    }

    @Test
    @DisplayName("Should allow requests below limit and block on exceeding with HTTP 429")
    void shouldBlockWhenAnalyzeLimitExceeded() throws ServletException, IOException {
        String uri = "/api/analysis/" + UUID.randomUUID() + "/analyze";

        // First 5 requests should pass
        for (int i = 1; i <= 5; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
            request.setRemoteAddr("192.168.1.100");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getHeader("X-RateLimit-Limit")).isEqualTo("5");
            assertThat(response.getHeader("X-RateLimit-Remaining")).isEqualTo(String.valueOf(5 - i));
        }

        verify(filterChain, times(5)).doFilter(any(), any());

        // 6th request should be rejected with 429
        MockHttpServletRequest request6 = new MockHttpServletRequest("POST", uri);
        request6.setRemoteAddr("192.168.1.100");
        MockHttpServletResponse response6 = new MockHttpServletResponse();

        filter.doFilter(request6, response6, filterChain);

        assertThat(response6.getStatus()).isEqualTo(429);
        assertThat(response6.getHeader("Retry-After")).isEqualTo("60");
        assertThat(response6.getContentAsString()).contains("Too Many Requests");

        // Filter chain should NOT have been invoked for 6th request
        verify(filterChain, times(5)).doFilter(any(), any());
    }

    @Test
    @DisplayName("Should apply higher general limit for status endpoint")
    void shouldApplySeparateLimitForStatusEndpoint() throws ServletException, IOException {
        String uri = "/api/analysis/" + UUID.randomUUID() + "/status";

        // 10 requests to status should succeed (limit is 10)
        for (int i = 1; i <= 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", uri);
            request.setRemoteAddr("192.168.1.200");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, filterChain);
            assertThat(response.getStatus()).isEqualTo(200);
        }

        verify(filterChain, times(10)).doFilter(any(), any());

        // 11th request to status should be 429
        MockHttpServletRequest request11 = new MockHttpServletRequest("GET", uri);
        request11.setRemoteAddr("192.168.1.200");
        MockHttpServletResponse response11 = new MockHttpServletResponse();

        filter.doFilter(request11, response11, filterChain);
        assertThat(response11.getStatus()).isEqualTo(429);
    }
}
