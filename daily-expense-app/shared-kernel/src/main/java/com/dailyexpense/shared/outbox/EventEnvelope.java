package com.dailyexpense.shared.outbox;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard cross-service event wrapper for the transactional outbox relay (CQ-8 / Doc 08 §1.2).
 * Exactly 8 fields — none may be added without a Constitution amendment.
 */
public record EventEnvelope(
    UUID eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    String producer,
    UUID userId,
    String traceId,
    String payload
) {

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private UUID eventId = UUID.randomUUID();
        private String eventType;
        private String eventVersion = "1.0";
        private Instant occurredAt = Instant.now();
        private String producer;
        private UUID userId;
        private String traceId;
        private String payload;

        private Builder() {}

        public Builder eventId(UUID id) { this.eventId = id; return this; }
        public Builder eventType(String t) { this.eventType = t; return this; }
        public Builder eventVersion(String v) { this.eventVersion = v; return this; }
        public Builder occurredAt(Instant ts) { this.occurredAt = ts; return this; }
        public Builder producer(String p) { this.producer = p; return this; }
        public Builder userId(UUID uid) { this.userId = uid; return this; }
        public Builder traceId(String tid) { this.traceId = tid; return this; }
        public Builder payload(String pl) { this.payload = pl; return this; }

        public EventEnvelope build() {
            return new EventEnvelope(eventId, eventType, eventVersion,
                occurredAt, producer, userId, traceId, payload);
        }
    }
}
