package com.foodrisk.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Milestone M13 Request Correlation Filter.
 *
 * Enforces correlation tracking across all incoming HTTP requests:
 * - Preserves existing X-Request-ID header if provided.
 * - Generates cryptographically secure UUID requestId if absent.
 * - Injects requestId into SLF4J MDC context for structured logging.
 * - Emits X-Request-ID header on HTTP response.
 * - Guaranteed cleanup in finally block.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingId = request.getHeader(REQUEST_ID_HEADER);
        String requestId = (incomingId != null && !incomingId.isBlank())
                ? incomingId.trim()
                : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
