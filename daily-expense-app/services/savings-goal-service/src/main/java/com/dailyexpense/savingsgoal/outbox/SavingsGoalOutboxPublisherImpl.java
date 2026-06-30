package com.dailyexpense.savingsgoal.outbox;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxEntry;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * T095 — Writes event to savings_goal_db.outbox in Propagation.MANDATORY (same tx as state change, CQ-8).
 */
@Component
public class SavingsGoalOutboxPublisherImpl implements OutboxPublisher {

    private final SavingsGoalOutboxRepository repository;

    public SavingsGoalOutboxPublisherImpl(SavingsGoalOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(EventEnvelope envelope) {
        SavingsGoalOutboxEntry entry = new SavingsGoalOutboxEntry();
        OutboxEntry.initialize(
            entry,
            envelope.userId(),
            "SavingsGoal",
            envelope.eventType(),
            envelope.eventId(),
            envelope.payload()
        );
        repository.save(entry);
    }
}
