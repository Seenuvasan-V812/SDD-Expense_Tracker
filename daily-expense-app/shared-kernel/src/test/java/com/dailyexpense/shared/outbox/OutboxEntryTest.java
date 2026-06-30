package com.dailyexpense.shared.outbox;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RED → GREEN: OutboxEntry maps all 9 required fields; OutboxPublisher declares publish(EventEnvelope).
 */
class OutboxEntryTest {

    @Test
    void outboxEntryHasAllNineRequiredFields() {
        Set<String> declaredFields = Arrays.stream(OutboxEntry.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(declaredFields).contains(
            "id",
            "eventId",
            "aggregateType",
            "aggregateId",
            "eventType",
            "payload",
            "published",
            "createdAt",
            "publishedAt"
        );
    }

    @Test
    void outboxPublisherDeclaresPublishMethod() throws NoSuchMethodException {
        var method = OutboxPublisher.class.getMethod("publish", EventEnvelope.class);
        assertThat(method).isNotNull();
    }

    @Test
    void outboxEntryCanBeInstantiated() {
        OutboxEntry entry = new OutboxEntry();
        entry.setId(UUID.randomUUID());
        entry.setEventId(UUID.randomUUID());
        entry.setAggregateType("Expense");
        entry.setAggregateId(UUID.randomUUID());
        entry.setEventType("ExpenseCreatedEvent");
        entry.setPayload("{\"amount\":\"100.00\"}");
        entry.setPublished(false);
        entry.setCreatedAt(Instant.now());
        entry.setPublishedAt(null);

        assertThat(entry.getAggregateType()).isEqualTo("Expense");
        assertThat(entry.isPublished()).isFalse();
        assertThat(entry.getPublishedAt()).isNull();
    }

    @Test
    void eventEnvelopeHasAllEightFields() {
        Set<String> fields = Arrays.stream(EventEnvelope.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toSet());

        assertThat(fields).contains(
            "eventId",
            "eventType",
            "eventVersion",
            "occurredAt",
            "producer",
            "userId",
            "traceId",
            "payload"
        );
    }

    @Test
    void eventEnvelopeRoundTrip() {
        UUID eventId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EventEnvelope env = new EventEnvelope(
            eventId, "ExpenseCreatedEvent", "1.0",
            Instant.now(), "expense-service", userId,
            "trace-001", "{}"
        );

        assertThat(env.eventId()).isEqualTo(eventId);
        assertThat(env.userId()).isEqualTo(userId);
        assertThat(env.producer()).isEqualTo("expense-service");
        assertThat(env.traceId()).isEqualTo("trace-001");
    }
}
