package com.dailyexpense.expense.outbox;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxEntry;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * T057 — Writes event to expense_db.outbox in Propagation.MANDATORY (same tx as state change, CQ-8).
 */
@Component
public class ExpenseOutboxPublisherImpl implements OutboxPublisher {

    private final ExpenseOutboxRepository repository;

    public ExpenseOutboxPublisherImpl(ExpenseOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(EventEnvelope envelope) {
        ExpenseOutboxEntry entry = new ExpenseOutboxEntry();
        OutboxEntry.initialize(
            entry,
            envelope.userId(),
            "Expense",
            envelope.eventType(),
            envelope.eventId(),
            envelope.payload()
        );
        repository.save(entry);
    }
}
