package com.dailyexpense.expense.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * T097 — Idempotent-consume guard for expense-service event consumers.
 *
 * Pattern: insert-before-process.
 * <ol>
 *   <li>Call {@link #markAndCheck} inside the same {@code @Transactional} as the business effect.</li>
 *   <li>Returns {@code true} → new event; proceed with processing.</li>
 *   <li>Returns {@code false} → duplicate delivery; skip without side-effects.</li>
 * </ol>
 * Duplicate Kafka delivery of the same {@code eventId} produces a single effect.
 */
@Component
public class ProcessedEventGuard {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Tries to mark {@code eventId} as processed within the caller's transaction
     * (Propagation.MANDATORY — caller MUST have an active tx).
     *
     * @return {@code true} if this is the first delivery; {@code false} if already processed.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean markAndCheck(UUID eventId, String eventType) {
        try {
            jdbcTemplate.update(
                "INSERT INTO processed_events(event_id, event_type, processed_at) VALUES(?, ?, now())",
                eventId, eventType);
            return true;
        } catch (DuplicateKeyException e) {
            log.debug("Duplicate event skipped: eventId={} eventType={}", eventId, eventType);
            return false;
        }
    }
}
