package com.dailyexpense.user.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * T037 — Per-IP auth rate-limit filter (SEC-4).
 * Applies only to login and forgot-password endpoints.
 * Returns 429 with integer Retry-After on breach.
 */
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final String LOGIN_PATH = "/api/v1/auth/login";
    private static final String FORGOT_PATH = "/api/v1/auth/forgot-password";

    private final int ipLimit;
    private final int windowSecs;

    private final ConcurrentHashMap<String, WindowCounter> ipCounters = new ConcurrentHashMap<>();

    public AuthRateLimitFilter(int ipLimit, int windowSecs) {
        this.ipLimit = ipLimit;
        this.windowSecs = windowSecs;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        if (!isRateLimitedPath(request)) {
            chain.doFilter(request, response);
            return;
        }

        String ip = resolveClientIp(request);
        WindowCounter counter = ipCounters.computeIfAbsent(ip, k -> new WindowCounter(windowSecs));

        if (counter.incrementAndCheckExceeded(ipLimit)) {
            log.warn("Rate limit exceeded ip={}", ip);
            response.setStatus(429);
            response.setContentType("application/json");
            response.setIntHeader("Retry-After", windowSecs); // integer (SEC-4 / T006 AC)
            response.getWriter().write(
                "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Too many requests\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private boolean isRateLimitedPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri.equals(LOGIN_PATH) || uri.equals(FORGOT_PATH);
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isEmpty())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }

    // Sliding-window counter with epoch-second granularity
    private static final class WindowCounter {
        private final int windowSecs;
        private long windowStart = Instant.now().getEpochSecond();
        private final AtomicInteger count = new AtomicInteger(0);

        WindowCounter(int windowSecs) {
            this.windowSecs = windowSecs;
        }

        synchronized boolean incrementAndCheckExceeded(int limit) {
            long now = Instant.now().getEpochSecond();
            if (now - windowStart >= windowSecs) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() > limit;
        }
    }
}
