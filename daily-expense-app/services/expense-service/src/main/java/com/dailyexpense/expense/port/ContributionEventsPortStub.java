package com.dailyexpense.expense.port;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * T058 — Phase 1 stub: publishes contribution events to expense_db.outbox.
 * No savings_goal_db SQL (AL-1). Replaced by messaging adapter in Phase 2.
 */
@Component
public class ContributionEventsPortStub implements ContributionEventsPort {

    private final OutboxPublisher outboxPublisher;

    public ContributionEventsPortStub(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseLinkedToGoal(UUID userId, UUID expenseId, UUID savingsGoalId, BigDecimal amount) {
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseLinkedToSavingsGoalEvent")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"savingsGoalId\":\"" + savingsGoalId + "\",\"userId\":\"" + userId
                + "\",\"expenseId\":\"" + expenseId + "\",\"amount\":\"" + amount
                + "\",\"date\":\"" + java.time.LocalDate.now() + "\"}")
            .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseGoalLinkChanged(UUID userId, UUID expenseId, UUID savingsGoalId,
                                       BigDecimal oldAmount, BigDecimal newAmount) {
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ContributionAmountAdjustedEvent")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"savingsGoalId\":\"" + savingsGoalId + "\",\"userId\":\"" + userId
                + "\",\"expenseId\":\"" + expenseId + "\",\"oldAmount\":\"" + oldAmount
                + "\",\"newAmount\":\"" + newAmount + "\"}")
            .build());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void expenseUnlinkedFromGoal(UUID userId, UUID expenseId, UUID savingsGoalId, BigDecimal amount) {
        outboxPublisher.publish(EventEnvelope.builder()
            .eventType("ExpenseUnlinkedFromSavingsGoalEvent")
            .userId(userId)
            .producer("expense-service")
            .payload("{\"savingsGoalId\":\"" + savingsGoalId + "\",\"userId\":\"" + userId
                + "\",\"expenseId\":\"" + expenseId + "\",\"amount\":\"" + amount + "\"}")
            .build());
    }
}
