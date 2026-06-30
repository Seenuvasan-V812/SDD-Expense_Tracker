package com.dailyexpense.shared.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Generates or propagates a traceId for every request, puts it in MDC, and clears it after
 * the response is committed (prevents bleed between requests — CQ-11 / P7).
 */
public class TraceIdFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = (incoming != null && !incoming.isBlank())
            ? incoming
            : UUID.randomUUID().toString();

        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY); // always clear — no bleed between requests
        }
    }
}
