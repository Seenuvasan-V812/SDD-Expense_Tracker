package com.dailyexpense.expense;

import com.dailyexpense.expense.consumer.ProcessedEventGuard;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.StoragePort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T097 gate — ProcessedEventGuardIT.
 * Proves: duplicate eventId → single effect (insert-before-process pattern).
 * AC: first delivery → markAndCheck returns true; second delivery (same eventId) → returns false.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProcessedEventGuardIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    @Autowired
    ProcessedEventGuard processedEventGuard;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager txManager;

    @Test
    void firstDelivery_returnsTrue_rowInserted() {
        UUID eventId = UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        Boolean result = tx.execute(status ->
            processedEventGuard.markAndCheck(eventId, "ExpenseCreated"));

        assertThat(result).isTrue();

        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_events WHERE event_id = ?",
            Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void duplicateDelivery_returnsFalse_noDoubleEffect() {
        UUID eventId = UUID.randomUUID();

        TransactionTemplate tx1 = new TransactionTemplate(txManager);
        Boolean first = tx1.execute(status ->
            processedEventGuard.markAndCheck(eventId, "ExpenseCreated"));
        assertThat(first).isTrue(); // first delivery processed

        TransactionTemplate tx2 = new TransactionTemplate(txManager);
        Boolean second = tx2.execute(status ->
            processedEventGuard.markAndCheck(eventId, "ExpenseCreated"));
        assertThat(second).isFalse(); // duplicate skipped — single effect proven

        // Still only ONE row in processed_events
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM processed_events WHERE event_id = ?",
            Integer.class, eventId);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void distinctEventIds_bothProcessed() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        TransactionTemplate tx = new TransactionTemplate(txManager);
        Boolean r1 = tx.execute(s -> processedEventGuard.markAndCheck(id1, "EventA"));
        Boolean r2 = tx.execute(s -> processedEventGuard.markAndCheck(id2, "EventB"));

        assertThat(r1).isTrue();
        assertThat(r2).isTrue();
    }

    @Test
    void processedEventsTable_hasPrimaryKeyConstraint() {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM information_schema.table_constraints " +
            "WHERE table_name='processed_events' AND constraint_type='PRIMARY KEY'",
            Integer.class);
        assertThat(count).isGreaterThan(0);
    }
}
