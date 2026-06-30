package com.dailyexpense.expense.port;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T059 — Phase 1 stub: publishes spending events to expense_db.outbox.
 * No budget_db SQL (AL-1). Replaced by messaging adapter in Phase 2.
 */
@Component
public class SpendingFeedPortStub implements SpendingFeedPort {

    private final OutboxPublisher outboxPublisher;

    public SpendingFeedPortStub(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseCreated(UUID userId, UUID expenseId, BigDecimal amount,
                               UUID categoryId, LocalDate date) {
        String catPart = categoryId != null ? ",\"categoryId\":\"" + categoryId + "\"" : ",\"categoryId\":null";
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseCreatedEvent")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"userId\":\"" + userId + "\",\"expenseId\":\"" + expenseId
                + "\",\"amount\":\"" + amount + "\"" + catPart + ",\"date\":\"" + date + "\"}")
            .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseUpdated(UUID userId, UUID expenseId, BigDecimal oldAmount, BigDecimal newAmount,
                               UUID categoryId, LocalDate date) {
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("SpendingExpenseUpdated")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"expenseId\":\"" + expenseId + "\",\"oldAmount\":\"" + oldAmount
                + "\",\"newAmount\":\"" + newAmount + "\",\"categoryId\":\"" + categoryId
                + "\",\"date\":\"" + date + "\"}")
            .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseDeleted(UUID userId, UUID expenseId, BigDecimal amount,
                               UUID categoryId, LocalDate date) {
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("SpendingExpenseDeleted")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"expenseId\":\"" + expenseId + "\",\"amount\":\"" + amount
                + "\",\"categoryId\":\"" + categoryId + "\",\"date\":\"" + date + "\"}")
            .build());
    }
}
