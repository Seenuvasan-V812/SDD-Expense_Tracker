package com.dailyexpense.budget.outbox;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxEntry;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * T095 — Writes event to budget_db.outbox in Propagation.MANDATORY (same tx as state change, CQ-8).
 */
@Component
public class BudgetOutboxPublisherImpl implements OutboxPublisher {

    private final BudgetOutboxRepository repository;

    public BudgetOutboxPublisherImpl(BudgetOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(EventEnvelope envelope) {
        BudgetOutboxEntry entry = new BudgetOutboxEntry();
        OutboxEntry.initialize(
            entry,
            envelope.userId(),
            "Budget",
            envelope.eventType(),
            envelope.eventId(),
            envelope.payload()
        );
        repository.save(entry);
    }
}
