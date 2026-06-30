package com.dailyexpense.expense.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * T119 — Consumes SavingsGoalDeletedEvent from savings-goal-service.
 *
 * On goal deletion: UPDATE expenses SET savings_goal_id=NULL
 *                   WHERE savings_goal_id=:goalId AND user_id=:userId
 *
 * Invariants:
 *   - Expenses are NOT deleted (SG-INV-8).
 *   - Idempotent via expense_db.processed_events (dup eventId → no second UPDATE).
 *   - No savings_goal_db SQL (AL-1).
 */
@Component
public class SavingsGoalDeletedEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SavingsGoalDeletedEventConsumer.class);
    private static final String EVENT_TYPE = "SavingsGoalDeletedEvent";

    private final ObjectMapper objectMapper;
    private final ProcessedEventGuard processedEventGuard;
    private final JdbcTemplate jdbcTemplate;

    public SavingsGoalDeletedEventConsumer(ObjectMapper objectMapper,
                                            ProcessedEventGuard processedEventGuard,
                                            JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.processedEventGuard = processedEventGuard;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(
        topics   = "${kafka.topics.savings-goals:savings-goals}",
        groupId  = "${spring.kafka.consumer.group-id:expense-service}"
    )
    @Transactional
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);

            if (!EVENT_TYPE.equals(envelope.path("eventType").asText())) {
                return;
            }

            UUID eventId = UUID.fromString(envelope.path("eventId").asText());
            if (!processedEventGuard.markAndCheck(eventId, EVENT_TYPE)) {
                log.debug("Duplicate SavingsGoalDeletedEvent skipped eventId={}", eventId);
                return;
            }

            JsonNode payload = objectMapper.readTree(envelope.path("payload").asText());
            UUID goalId = UUID.fromString(payload.path("goalId").asText());
            UUID userId = UUID.fromString(payload.path("userId").asText());
            String traceId = envelope.path("traceId").asText(null);

            MDC.put("userId", userId.toString());
            if (traceId != null) MDC.put("traceId", traceId);
            try {
                int updated = jdbcTemplate.update(
                    "UPDATE expenses SET savings_goal_id = NULL " +
                    "WHERE savings_goal_id = ? AND user_id = ?",
                    goalId, userId);

                log.info("SavingsGoalDeleted: cleared savings_goal_id on {} expense(s) goalId={}", updated, goalId);
            } finally {
                MDC.remove("userId");
                MDC.remove("traceId");
            }

        } catch (Exception e) {
            log.error("Failed to process SavingsGoalDeletedEvent: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }
}
