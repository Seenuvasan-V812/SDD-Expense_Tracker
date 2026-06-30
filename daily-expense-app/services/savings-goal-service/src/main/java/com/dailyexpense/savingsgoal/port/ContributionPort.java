package com.dailyexpense.savingsgoal.port;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T073 — Anti-corruption port: instructs expense-service to create a backing Expense
 * under the Savings Category. No expense_db SQL in savings-goal-service (AL-1).
 */
public interface ContributionPort {

    /**
     * Creates a backing Expense in expense-service with categoryId=Savings Category,
     * savingsGoalId set, and the given amount/date.
     *
     * @return the created expense's UUID
     */
    UUID createBackingExpense(UUID userId, BigDecimal amount, LocalDate date, UUID savingsGoalId, String bearerToken);
}
