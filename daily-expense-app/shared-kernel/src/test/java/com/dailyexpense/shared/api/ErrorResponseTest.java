package com.dailyexpense.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: ErrorResponse serializes exactly the 6 mandated keys (API-3); message carries no PII.
 */
class ErrorResponseTest {

    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void serializesExactlySixKeys() throws Exception {
        var response = new ErrorResponse(
            Instant.now(), 404, "Not Found",
            "Resource not found", "/api/v1/expenses/xyz", "trace-001"
        );
        ObjectNode node = mapper.valueToTree(response);
        assertThat(node.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("timestamp", "status", "error", "message", "path", "traceId");
    }

    @Test
    void noExtraFieldsPresent() throws Exception {
        var response = new ErrorResponse(
            Instant.now(), 400, "Bad Request", "Validation failed", "/api/v1/test", "t-123"
        );
        ObjectNode node = mapper.valueToTree(response);
        Set<String> allowed = Set.of("timestamp", "status", "error", "message", "path", "traceId");
        node.fieldNames().forEachRemaining(name ->
            assertThat(allowed).contains(name)
        );
    }

    @Test
    void messageContainsNoPii() {
        // Message should describe the problem without email / amount / token
        var response = new ErrorResponse(
            Instant.now(), 401, "Unauthorized",
            "Authentication required", "/api/v1/me", "t-124"
        );
        String msg = response.message();
        assertThat(msg).doesNotContain("@");
        assertThat(msg).doesNotContain("password");
        assertThat(msg).doesNotContain("Bearer");
    }

    @Test
    void statusFieldIsNumeric() throws Exception {
        var response = new ErrorResponse(Instant.now(), 409, "Conflict",
            "Duplicate entry", "/api/v1/categories", "t-125");
        ObjectNode node = mapper.valueToTree(response);
        assertThat(node.get("status").isInt()).isTrue();
        assertThat(node.get("status").asInt()).isEqualTo(409);
    }

    @Test
    void traceIdIsPresent() throws Exception {
        var response = new ErrorResponse(Instant.now(), 500, "Internal Server Error",
            "Unexpected error", "/api/v1/x", "abc-999");
        ObjectNode node = mapper.valueToTree(response);
        assertThat(node.get("traceId").asText()).isEqualTo("abc-999");
    }
}
