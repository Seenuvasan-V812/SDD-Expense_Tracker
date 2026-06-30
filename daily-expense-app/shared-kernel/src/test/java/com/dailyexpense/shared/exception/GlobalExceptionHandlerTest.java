package com.dailyexpense.shared.exception;

import com.dailyexpense.shared.api.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * RED → GREEN: GlobalExceptionHandler — each mapped exception → correct status + ErrorResponse with 0 PII (API-3).
 * Uses a standalone MockMvc with inline test controllers that throw each exception type.
 */
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @RestController
    static class ThrowForbidden {
        @GetMapping("/test/forbidden")
        String get() { throw new ForbiddenOwnershipException("user-123"); }
    }

    @RestController
    static class ThrowNotFound {
        @GetMapping("/test/not-found")
        String get() { throw new ResourceNotFoundException("Expense", "abc-123"); }
    }

    @RestController
    static class ThrowConflict {
        @GetMapping("/test/conflict")
        String get() { throw new BusinessConflictException("Email already registered"); }
    }

    @RestController
    static class ThrowRateLimit {
        @GetMapping("/test/rate-limit")
        String get() { throw new RateLimitExceededException(60); }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
            .standaloneSetup(
                new ThrowForbidden(), new ThrowNotFound(),
                new ThrowConflict(), new ThrowRateLimit()
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void forbiddenOwnershipExceptionReturns403() throws Exception {
        var result = mockMvc.perform(get("/test/forbidden"))
            .andExpect(status().isForbidden())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn();

        ErrorResponse body = mapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.status()).isEqualTo(403);
        assertThat(body.traceId()).isNotNull();
        assertThat(body.message()).doesNotContain("user-123"); // no PII: no userId in message
        assertThat(body.message()).doesNotContain("@");
    }

    @Test
    void resourceNotFoundReturns404() throws Exception {
        var result = mockMvc.perform(get("/test/not-found"))
            .andExpect(status().isNotFound())
            .andReturn();

        ErrorResponse body = mapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.status()).isEqualTo(404);
        assertThat(body.error()).containsIgnoringCase("not found");
    }

    @Test
    void businessConflictReturns409() throws Exception {
        var result = mockMvc.perform(get("/test/conflict"))
            .andExpect(status().isConflict())
            .andReturn();

        ErrorResponse body = mapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.status()).isEqualTo(409);
    }

    @Test
    void rateLimitExceptionReturns429WithRetryAfter() throws Exception {
        var result = mockMvc.perform(get("/test/rate-limit"))
            .andExpect(status().isTooManyRequests())
            .andReturn();

        ErrorResponse body = mapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertThat(body.status()).isEqualTo(429);
        // Retry-After header must be an integer seconds value
        String retryAfter = result.getResponse().getHeader("Retry-After");
        assertThat(retryAfter).isNotNull();
        assertThatCode(() -> Integer.parseInt(retryAfter)).doesNotThrowAnyException();
    }

    @Test
    void errorResponseHasCorrectShape() throws Exception {
        var result = mockMvc.perform(get("/test/forbidden"))
            .andReturn();

        var node = mapper.readTree(result.getResponse().getContentAsString());
        assertThat(node.has("timestamp")).isTrue();
        assertThat(node.has("status")).isTrue();
        assertThat(node.has("error")).isTrue();
        assertThat(node.has("message")).isTrue();
        assertThat(node.has("path")).isTrue();
        assertThat(node.has("traceId")).isTrue();
        // No extra keys beyond the 6
        assertThat(node.size()).isEqualTo(6);
    }

    @Test
    void forbiddenIsNever404() throws Exception {
        // INV-1 / SEC-3: ownership failure must be 403, NEVER 404
        mockMvc.perform(get("/test/forbidden"))
            .andExpect(status().isForbidden());
    }
}
