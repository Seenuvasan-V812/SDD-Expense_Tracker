package com.dailyexpense.shared.outbox;

/**
 * Contract for writing an event to the transactional outbox in the same {@code @Transactional}
 * unit as the state change that produced it (CQ-8).
 *
 * Implementations live in each service's {@code outbox} package; they hold a reference to
 * the service-specific JPA repository for the outbox table.
 */
public interface OutboxPublisher {

    /**
     * Writes the given event envelope to the outbox table in the current transaction.
     * Must be called inside an active transaction — if no transaction is active, the
     * implementation must throw {@link IllegalStateException}.
     *
     * @param envelope the event to persist; must not be null
     */
    void publish(EventEnvelope envelope);
}
