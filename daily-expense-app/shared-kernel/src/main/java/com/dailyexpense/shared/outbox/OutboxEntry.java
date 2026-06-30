package com.dailyexpense.shared.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

import java.time.Instant;
import java.util.UUID;

/**
 * Mapped superclass for the per-service transactional outbox table (CQ-8 / Doc 09 §7.1).
 * Each service extends this with {@code @Entity @Table(name="outbox")} in its own schema.
 *
 * Fields (9): id, event_id, aggregate_type, aggregate_id, event_type, payload, published,
 * created_at, published_at.
 */
@MappedSuperclass
public class OutboxEntry {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true, updatable = false)
    private UUID eventId;

    @Column(name = "aggregate_type", nullable = false, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, updatable = false)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEntry() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getEventId() { return eventId; }
    public void setEventId(UUID eventId) { this.eventId = eventId; }

    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }

    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID aggregateId) { this.aggregateId = aggregateId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public boolean isPublished() { return published; }
    public void setPublished(boolean published) { this.published = published; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }

    /** Factory method — pre-populates id + createdAt. */
    public static void initialize(OutboxEntry entry, UUID aggregateId,
                                      String aggregateType, String eventType,
                                      UUID eventId, String payload) {
        entry.id = UUID.randomUUID();
        entry.eventId = eventId;
        entry.aggregateId = aggregateId;
        entry.aggregateType = aggregateType;
        entry.eventType = eventType;
        entry.payload = payload;
        entry.published = false;
        entry.createdAt = Instant.now();
    }
}
