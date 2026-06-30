package com.dailyexpense.expense.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T059 — Port for emitting spending events consumed by budget-service.
 * Implementations write to the outbox with no budget_db SQL (AL-1).
 */
public interface SpendingFeedPort {
    void expenseCreated(UUID userId, UUID expenseId, BigDecimal amount, UUID categoryId, LocalDate date);
    void expenseUpdated(UUID userId, UUID expenseId, BigDecimal oldAmount, BigDecimal newAmount,
                        UUID categoryId, LocalDate date);
    void expenseDeleted(UUID userId, UUID expenseId, BigDecimal amount, UUID categoryId, LocalDate date);
}
