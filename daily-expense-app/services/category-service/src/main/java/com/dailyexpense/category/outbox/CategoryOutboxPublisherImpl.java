package com.dailyexpense.category.outbox;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxEntry;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * T095 — Writes event to category_db.outbox in Propagation.MANDATORY (same tx as state change, CQ-8).
 * Rollback of the caller's tx rolls back this insert — no orphan outbox rows.
 */
@Component
public class CategoryOutboxPublisherImpl implements OutboxPublisher {

    private final CategoryOutboxRepository repository;

    public CategoryOutboxPublisherImpl(CategoryOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(EventEnvelope envelope) {
        CategoryOutboxEntry entry = new CategoryOutboxEntry();
        OutboxEntry.initialize(
            entry,
            envelope.userId(),
            "Category",
            envelope.eventType(),
            envelope.eventId(),
            envelope.payload()
        );
        repository.save(entry);
    }
}
