package com.dailyexpense.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T098 gate — EventEnvelopeTest.
 * AC: exactly 8 fields; round-trip identical; none missing in JSON.
 */
class EventEnvelopeTest {

    private final ObjectMapper om = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void hasExactlyEightFields() {
        Set<String> fields = Arrays.stream(EventEnvelope.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());

        assertThat(fields).containsExactlyInAnyOrder(
            "eventId", "eventType", "eventVersion", "occurredAt",
            "producer", "userId", "traceId", "payload"
        );
        assertThat(fields).hasSize(8);
    }

    @Test
    void jsonRoundTrip_allEightFieldsSurvive() throws Exception {
        UUID eventId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID userId  = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Instant ts   = Instant.parse("2026-06-28T10:00:00Z");

        EventEnvelope original = EventEnvelope.builder()
            .eventId(eventId)
            .eventType("ExpenseCreated")
            .eventVersion("1.0")
            .occurredAt(ts)
            .producer("expense-service")
            .userId(userId)
            .traceId("trace-abc-123")
            .payload("{\"amount\":\"100.00\"}")
            .build();

        String json = om.writeValueAsString(original);
        EventEnvelope restored = om.readValue(json, EventEnvelope.class);

        assertThat(restored.eventId()).isEqualTo(eventId);
        assertThat(restored.eventType()).isEqualTo("ExpenseCreated");
        assertThat(restored.eventVersion()).isEqualTo("1.0");
        assertThat(restored.occurredAt()).isEqualTo(ts);
        assertThat(restored.producer()).isEqualTo("expense-service");
        assertThat(restored.userId()).isEqualTo(userId);
        assertThat(restored.traceId()).isEqualTo("trace-abc-123");
        assertThat(restored.payload()).isEqualTo("{\"amount\":\"100.00\"}");
    }

    @Test
    void jsonFields_noneAbsent() throws Exception {
        EventEnvelope envelope = EventEnvelope.builder()
            .eventType("TestEvent")
            .producer("test-service")
            .userId(UUID.randomUUID())
            .traceId("t-1")
            .payload("{}")
            .build();

        String json = om.writeValueAsString(envelope);

        assertThat(json).contains("\"eventId\"");
        assertThat(json).contains("\"eventType\"");
        assertThat(json).contains("\"eventVersion\"");
        assertThat(json).contains("\"occurredAt\"");
        assertThat(json).contains("\"producer\"");
        assertThat(json).contains("\"userId\"");
        assertThat(json).contains("\"traceId\"");
        assertThat(json).contains("\"payload\"");
    }

    @Test
    void builder_defaultsEventIdAndVersion() {
        EventEnvelope e = EventEnvelope.builder()
            .eventType("SomeEvent")
            .producer("svc")
            .userId(UUID.randomUUID())
            .payload("{}")
            .build();

        assertThat(e.eventId()).isNotNull();
        assertThat(e.eventVersion()).isEqualTo("1.0");
        assertThat(e.occurredAt()).isNotNull();
    }
}
