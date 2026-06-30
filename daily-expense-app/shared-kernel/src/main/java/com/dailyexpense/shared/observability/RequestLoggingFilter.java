package com.dailyexpense.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

/**
 * Logs one line per request: method | path | status | latencyMs | traceId.
 * Zero PII — no email, amount, password, or Authorization header is logged (CQ-11 / CQ-13).
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        long start = System.currentTimeMillis();
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        try {
            chain.doFilter(request, wrappedResponse);
        } finally {
            long latency = System.currentTimeMillis() - start;
            String traceId = MDC.get("traceId");

            // Log ONLY: method, sanitised path, status, latency, traceId — NO request body/headers
            log.info("method={} path={} status={} latency={}ms traceId={}",
                request.getMethod(),
                sanitisePath(request.getRequestURI()),
                wrappedResponse.getStatus(),
                latency,
                traceId != null ? traceId : "unknown"
            );

            wrappedResponse.copyBodyToResponse();
        }
    }

    /** Strips any UUID path parameters that could contain sensitive ids from the log line. */
    private String sanitisePath(String uri) {
        if (uri == null) return "";
        // Replace UUID segments (no user data in path, but normalise for cardinality)
        return uri.replaceAll(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}",
            ":id"
        );
    }
}
