package com.foodrisk.security.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * M12 Rate Limiting Filter: Differential tier rate limiting.
 *
 * Tier 1: POST /api/analysis/{sessionId}/analyze -> 10 requests per minute per IP.
 * Tier 2: /api/analysis/{sessionId}/status & /assessment -> 60 requests per minute per IP.
 *
 * Exceeding requests receive HTTP 429 Too Many Requests with Retry-After header.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitingFilter.class);

    private final int analyzeLimitPerMinute;
    private final int generalLimitPerMinute;

    private final Map<String, RequestCounter> clientCounters = new ConcurrentHashMap<>();

    public RateLimitingFilter(
            @Value("${rate-limit.analyze-per-minute:10}") int analyzeLimitPerMinute,
            @Value("${rate-limit.general-per-minute:60}") int generalLimitPerMinute
    ) {
        this.analyzeLimitPerMinute = analyzeLimitPerMinute;
        this.generalLimitPerMinute = generalLimitPerMinute;
    }

    private static class RequestCounter {
        private final long windowStartEpochSecond;
        private final AtomicInteger count;

        RequestCounter(long windowStartEpochSecond) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = new AtomicInteger(1);
        }
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Apply rate limits to analysis endpoints
        if (path.startsWith("/api/analysis")) {
            boolean isAnalyzeEndpoint = method.equalsIgnoreCase("POST") && path.endsWith("/analyze");
            int maxAllowed = isAnalyzeEndpoint ? analyzeLimitPerMinute : generalLimitPerMinute;
            String category = isAnalyzeEndpoint ? "analyze" : "general";

            String clientIp = extractClientIp(request);
            String key = clientIp + ":" + category;

            long currentMinute = Instant.now().getEpochSecond() / 60;

            RequestCounter counter = clientCounters.compute(key, (k, existing) -> {
                if (existing == null || existing.windowStartEpochSecond != currentMinute) {
                    return new RequestCounter(currentMinute);
                }
                existing.count.incrementAndGet();
                return existing;
            });

            // Opportunistic cleanup of stale windows
            if (clientCounters.size() > 5000) {
                clientCounters.entrySet().removeIf(e -> e.getValue().windowStartEpochSecond < currentMinute);
            }

            int currentRequests = counter.count.get();
            int remaining = Math.max(0, maxAllowed - currentRequests);

            response.setHeader("X-RateLimit-Limit", String.valueOf(maxAllowed));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(remaining));

            if (currentRequests > maxAllowed) {
                log.warn("Rate limit exceeded for client {} on [{}] {} ({}/min)", clientIp, method, path, maxAllowed);
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json");
                response.setHeader("Retry-After", "60");
                String reqId = org.slf4j.MDC.get("requestId");
                if (reqId == null) reqId = request.getHeader("X-Request-ID");
                response.getWriter().write(String.format(
                        "{\"timestamp\":\"%s\",\"status\":429,\"error\":\"Too Many Requests\",\"code\":\"RATE_LIMIT_EXCEEDED\",\"message\":\"Too Many Requests: Rate limit exceeded for %s. Max %d requests per minute allowed.\",\"path\":\"%s\",\"requestId\":%s}",
                        Instant.now(),
                        category,
                        maxAllowed,
                        path,
                        reqId != null ? "\"" + reqId + "\"" : "null"
                ));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
    }

    // Visible for testing
    public void reset() {
        clientCounters.clear();
    }
}
