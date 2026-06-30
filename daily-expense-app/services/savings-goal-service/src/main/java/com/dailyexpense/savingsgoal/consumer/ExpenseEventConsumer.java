package com.dailyexpense.savingsgoal.consumer;

import com.dailyexpense.savingsgoal.service.ContributionReconciliationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T076 — Secondary contribution flow: consumes ExpenseLinkedToSavingsGoalEvent.
 * T077 — Reconciliation: consumes ContributionAmountAdjustedEvent, ExpenseUnlinkedFromSavingsGoalEvent,
 *         ExpenseDeletedEvent (with savingsGoalId).
 * Idempotent via processed_events insert-before-process (T097).
 * No expense_db SQL (AL-1).
 */
@Component
public class ExpenseEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExpenseEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ContributionReconciliationService reconciliationService;
    private final JdbcTemplate jdbcTemplate;

    public ExpenseEventConsumer(ObjectMapper objectMapper,
                                ContributionReconciliationService reconciliationService,
                                JdbcTemplate jdbcTemplate) {
        this.objectMapper = objectMapper;
        this.reconciliationService = reconciliationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @KafkaListener(topics = "${kafka.topics.expenses:expenses}", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void consume(String message) {
        try {
            JsonNode envelope = objectMapper.readTree(message);
            String eventType = envelope.path("eventType").asText();
            String eventId = envelope.path("eventId").asText();

            if (!markAndCheck(UUID.fromString(eventId), eventType)) {
                log.debug("Duplicate event skipped: eventId={} type={}", eventId, eventType);
                return;
            }

            JsonNode payload = objectMapper.readTree(envelope.path("payload").asText());

            switch (eventType) {
                case "ExpenseLinkedToSavingsGoalEvent"   -> handleLinked(payload);
                case "ContributionAmountAdjustedEvent"   -> handleAmountAdjusted(payload);
                case "ExpenseUnlinkedFromSavingsGoalEvent" -> handleUnlinked(payload);
                case "ExpenseDeletedEvent"               -> handleExpenseDeleted(payload);
                default -> log.trace("Ignored event type={}", eventType);
            }
        } catch (Exception e) {
            log.error("Error consuming expense event: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }
    }

    private void handleLinked(JsonNode payload) {
        UUID goalId    = UUID.fromString(payload.path("savingsGoalId").asText());
        UUID userId    = UUID.fromString(payload.path("userId").asText());
        UUID expenseId = UUID.fromString(payload.path("expenseId").asText());
        BigDecimal amount = new BigDecimal(payload.path("amount").asText());
        LocalDate date = payload.has("date")
            ? LocalDate.parse(payload.path("date").asText()) : LocalDate.now();

        reconciliationService.onExpenseLinked(goalId, userId, expenseId, amount, date);
    }

    private void handleAmountAdjusted(JsonNode payload) {
        UUID goalId    = UUID.fromString(payload.path("savingsGoalId").asText());
        UUID userId    = UUID.fromString(payload.path("userId").asText());
        UUID expenseId = UUID.fromString(payload.path("expenseId").asText());
        BigDecimal newAmount = new BigDecimal(payload.path("newAmount").asText());

        reconciliationService.onExpenseAmountAdjusted(goalId, userId, expenseId, newAmount);
    }

    private void handleUnlinked(JsonNode payload) {
        UUID goalId    = UUID.fromString(payload.path("savingsGoalId").asText());
        UUID userId    = UUID.fromString(payload.path("userId").asText());
        UUID expenseId = UUID.fromString(payload.path("expenseId").asText());

        reconciliationService.onExpenseUnlinkedOrDeleted(goalId, userId, expenseId);
    }

    private void handleExpenseDeleted(JsonNode payload) {
        if (!payload.has("savingsGoalId") || payload.path("savingsGoalId").isNull()) return;

        UUID goalId    = UUID.fromString(payload.path("savingsGoalId").asText());
        UUID userId    = UUID.fromString(payload.path("userId").asText());
        UUID expenseId = UUID.fromString(payload.path("expenseId").asText());

        reconciliationService.onExpenseUnlinkedOrDeleted(goalId, userId, expenseId);
    }

    private boolean markAndCheck(UUID eventId, String eventType) {
        try {
            jdbcTemplate.update(
                "INSERT INTO processed_events(event_id, event_type, processed_at) VALUES(?, ?, now())",
                eventId, eventType);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }
}
