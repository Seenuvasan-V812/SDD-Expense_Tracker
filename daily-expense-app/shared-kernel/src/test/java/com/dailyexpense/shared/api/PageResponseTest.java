package com.dailyexpense.shared.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: PageResponse serializes exactly the 5 mandated keys (API-2).
 */
class PageResponseTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void serializesExactlyFiveKeys() throws Exception {
        var response = new PageResponse<>(List.of("a", "b"), 0, 20, 2L, 1);
        ObjectNode node = mapper.valueToTree(response);
        assertThat(node.fieldNames()).toIterable()
            .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
    }

    @Test
    void contentIsPreserved() throws Exception {
        var items = List.of("foo", "bar");
        var response = new PageResponse<>(items, 1, 10, 12L, 2);
        String json = mapper.writeValueAsString(response);
        assertThat(json).contains("\"foo\"").contains("\"bar\"");
    }

    @Test
    void paginationFieldsAreCorrect() throws Exception {
        var response = new PageResponse<>(List.of(), 2, 50, 150L, 3);
        ObjectNode node = mapper.valueToTree(response);
        assertThat(node.get("page").asInt()).isEqualTo(2);
        assertThat(node.get("size").asInt()).isEqualTo(50);
        assertThat(node.get("totalElements").asLong()).isEqualTo(150L);
        assertThat(node.get("totalPages").asInt()).isEqualTo(3);
    }

    @Test
    void genericTypeIsPreserved() {
        var response = new PageResponse<>(List.of(1, 2, 3), 0, 10, 3L, 1);
        assertThat(response.content()).isEqualTo(List.of(1, 2, 3));
    }

    @Test
    void noExtraFieldsPresent() throws Exception {
        var response = new PageResponse<>(List.of(), 0, 20, 0L, 0);
        ObjectNode node = mapper.valueToTree(response);
        Set<String> allowed = Set.of("content", "page", "size", "totalElements", "totalPages");
        node.fieldNames().forEachRemaining(name ->
            assertThat(allowed).contains(name)
        );
    }
}
