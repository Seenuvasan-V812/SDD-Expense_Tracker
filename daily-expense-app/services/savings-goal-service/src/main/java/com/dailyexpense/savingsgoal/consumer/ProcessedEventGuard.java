package com.dailyexpense.savingsgoal.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * T097 — Idempotent-consume guard for savings-goal-service event consumers.
 * insert-before-process pattern; duplicate eventId → returns false → consumer skips.
 */
@Component
public class ProcessedEventGuard {

    private static final Logger log = LoggerFactory.getLogger(ProcessedEventGuard.class);

    private final JdbcTemplate jdbcTemplate;

    public ProcessedEventGuard(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
