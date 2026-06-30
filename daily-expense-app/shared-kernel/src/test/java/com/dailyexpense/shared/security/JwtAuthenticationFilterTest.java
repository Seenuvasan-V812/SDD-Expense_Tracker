package com.dailyexpense.shared.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

/**
 * RED → GREEN: JwtAuthenticationFilter — valid JWT sets principal+MDC; invalid → 401; no PII in MDC.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-for-testing-only-at-least-32-chars-long";

    private JwtService jwtService;
    private JwtAuthenticationFilter filter;

    @Mock
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
        filter = new JwtAuthenticationFilter(jwtService);
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void validJwtSetsPrincipalInSecurityContext() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        // Capture auth during chain (filter clears context in finally after chain)
        final Authentication[] captured = {null};
        filter.doFilterInternal(request, response, (req, res) -> {
            captured[0] = SecurityContextHolder.getContext().getAuthentication();
        });

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getPrincipal()).isInstanceOf(CallerContext.class);
        CallerContext ctx = (CallerContext) captured[0].getPrincipal();
        assertThat(ctx.userId()).isEqualTo(userId);
    }

    @Test
    void validJwtSetsUserIdInMdc() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] mdcUserId = {null};
        filter.doFilterInternal(request, response, (req, res) -> {
            mdcUserId[0] = MDC.get("userId");
        });

        assertThat(mdcUserId[0]).isEqualTo(userId.toString());
    }

    @Test
    void mdcUserIdDoesNotContainEmail() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        final String[] mdcUserId = {null};
        filter.doFilterInternal(request, response, (req, res) -> {
            mdcUserId[0] = MDC.get("userId");
        });

        assertThat(mdcUserId[0]).doesNotContain("@");
    }

    @Test
    void invalidJwtReturns401() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer invalid.token.here");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void missingAuthHeaderPassesThroughWithNoAuth() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        // No auth set — Spring Security will handle the 401 via the entry point
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void mdcIsClearedAfterRequest() throws ServletException, IOException {
        UUID userId = UUID.randomUUID();
        String token = jwtService.issueAccessToken(userId);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        // MDC userId should be cleared after filter chain completes
        assertThat(MDC.get("userId")).isNull();
    }
}
