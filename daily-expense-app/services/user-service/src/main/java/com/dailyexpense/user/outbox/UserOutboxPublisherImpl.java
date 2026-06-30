package com.dailyexpense.user.outbox;

import com.dailyexpense.shared.outbox.EventEnvelope;
import com.dailyexpense.shared.outbox.OutboxEntry;
import com.dailyexpense.shared.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserOutboxPublisherImpl implements OutboxPublisher {

    private final UserOutboxRepository repository;

    public UserOutboxPublisherImpl(UserOutboxRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(EventEnvelope envelope) {
        UserOutboxEntry entry = new UserOutboxEntry();
        OutboxEntry.initialize(
                entry,
                envelope.userId(),
                "User",
                envelope.eventType(),
                envelope.eventId(),
                envelope.payload()
        );
        repository.save(entry);
    }
}
