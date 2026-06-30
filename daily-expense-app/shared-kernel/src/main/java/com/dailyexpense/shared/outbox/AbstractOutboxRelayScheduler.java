package com.dailyexpense.shared.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * T096 — Transactional outbox relay: poll published=false → publish EventEnvelope to Kafka
 * → mark published. Uses JdbcTemplate directly so it works with any service-specific outbox table.
 *
 * AL-1: each service subclass is wired to its OWN DataSource → no cross-schema SQL.
 * CQ-8: relay publishes events after they are committed to the outbox.
 */
public abstract class AbstractOutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(AbstractOutboxRelayScheduler.class);
    private static final int BATCH_SIZE = 50;
    private static final int KAFKA_TIMEOUT_SECONDS = 10;

    protected final JdbcTemplate jdbcTemplate;
    protected final KafkaTemplate<String, String> kafkaTemplate;
    protected final ObjectMapper objectMapper;
    protected final String topic;
    protected final String producer;

    protected AbstractOutboxRelayScheduler(JdbcTemplate jdbcTemplate,
                                           KafkaTemplate<String, String> kafkaTemplate,
                                           ObjectMapper objectMapper,
                                           String topic,
                                           String producer) {
        this.jdbcTemplate = jdbcTemplate;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.producer = producer;
    }

    /**
     * Polls the outbox for unpublished rows (via {@code idx_outbox_unpublished}),
     * publishes each as an {@link EventEnvelope} to Kafka, then marks the row published.
     * Each row is processed independently — one failure does not abort the batch.
     */
    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    public void relay() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            "SELECT id, event_id, event_type, aggregate_id, payload, created_at " +
            "FROM outbox WHERE published = false ORDER BY created_at LIMIT " + BATCH_SIZE);

        for (Map<String, Object> row : rows) {
            UUID outboxId  = (UUID) row.get("id");
            UUID eventId   = (UUID) row.get("event_id");
            String type    = (String) row.get("event_type");
            UUID userId    = (UUID) row.get("aggregate_id");
            String payload = (String) row.get("payload");
            Instant ts     = ((Timestamp) row.get("created_at")).toInstant();

            EventEnvelope envelope = EventEnvelope.builder()
                .eventId(eventId)
                .eventType(type)
                .occurredAt(ts)
                .producer(producer)
                .userId(userId)
                .payload(payload)
                .build();

            try {
                String json = objectMapper.writeValueAsString(envelope);
                kafkaTemplate.send(topic, eventId.toString(), json)
                    .get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);

                jdbcTemplate.update(
                    "UPDATE outbox SET published = true, published_at = now() WHERE id = ?",
                    outboxId);

                log.debug("Relayed eventId={} type={} to topic={}", eventId, type, topic);
            } catch (Exception e) {
                log.error("Relay failed for outbox id={} eventId={}: {}",
                    outboxId, eventId, e.getMessage());
            }
        }
    }
}
