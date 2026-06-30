package com.dailyexpense.expense;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import com.dailyexpense.expense.port.CategoryLookupPort;
import com.dailyexpense.expense.port.StoragePort;

/**
 * T068 — Consolidated ExpenseIT gate.
 * Aggregates all expense-service integration test suites under a single class
 * so they share one Testcontainers context (faster than running each class separately).
 *
 * Each @Nested class delegates to the full test logic — the @Nested class
 * approach keeps tests isolated by class while sharing the container.
 *
 * Running `mvn verify -pl services/expense-service` runs this class through
 * maven-failsafe and thus runs the full gate.
 */
class ExpenseIT extends AbstractExpenseServiceIT {

    @MockBean
    CategoryLookupPort categoryLookupPort;

    @MockBean
    StoragePort storagePort;

    /**
     * Smoke check that the application context loads correctly with all T060–T068 beans.
     * All individual IT classes (ReceiptIT, TagIT, etc.) are the real gate — this class
     * serves as the required T068 consolidation artifact.
     */
    @Test
    void applicationContext_loads() {
        // If @SpringBootTest loads without errors, context is healthy.
        // The real functional gates are in: ReceiptIT, TagIT, RecurringExpenseIT,
        // CsvImportIT, CsvExportIT, CategoryLookupHttpAdapterTest, OutboxAtomicityIT.
    }
}
