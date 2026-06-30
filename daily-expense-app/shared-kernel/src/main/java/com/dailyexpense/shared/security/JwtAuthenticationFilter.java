package com.dailyexpense.shared.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.UUID;

/**
 * Extracts and verifies the Bearer JWT on every request. On success, sets CallerContext as the
 * principal in the SecurityContext and places userId in MDC (never email/name — CQ-13 / AL-5).
 * On failure, returns 401 immediately without leaking token details.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            UUID userId = jwtService.verifyAccessToken(token);
            CallerContext ctx = new CallerContext(userId);
            var auth = new UsernamePasswordAuthenticationToken(ctx, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(auth);
            MDC.put("userId", userId.toString()); // UUID only — no email (CQ-13)

            try {
                chain.doFilter(request, response);
            } finally {
                MDC.remove("userId"); // prevent bleed between requests
                SecurityContextHolder.clearContext();
            }
        } catch (JwtException | IllegalArgumentException ex) {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or expired token\"}");
        }
    }
}
