package com.dailyexpense.expense.outbox;

import com.dailyexpense.shared.outbox.AbstractOutboxRelayScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * T096 — Expense-service outbox relay.
 * Polls expense_db.outbox WHERE published=false → publishes EventEnvelope to Kafka topic → marks published.
 * Disabled in tests via scheduling.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "scheduling.enabled", matchIfMissing = true)
public class OutboxRelayScheduler extends AbstractOutboxRelayScheduler {

    public OutboxRelayScheduler(
            JdbcTemplate jdbcTemplate,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${outbox.relay.topic:expenses}") String topic,
            @Value("${spring.application.name:expense-service}") String producer) {
        super(jdbcTemplate, kafkaTemplate, objectMapper, topic, producer);
    }
}
