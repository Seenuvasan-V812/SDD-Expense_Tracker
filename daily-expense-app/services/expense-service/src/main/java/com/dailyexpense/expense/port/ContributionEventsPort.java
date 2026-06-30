package com.dailyexpense.expense.port;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * T058 — Port for emitting contribution-related events when an expense is linked/changed/unlinked
 * from a savings goal. Implementations write to the outbox with no savings_goal_db SQL (AL-1).
 */
public interface ContributionEventsPort {
    void expenseLinkedToGoal(UUID userId, UUID expenseId, UUID savingsGoalId, BigDecimal amount);
    void expenseGoalLinkChanged(UUID userId, UUID expenseId, UUID savingsGoalId,
                                BigDecimal oldAmount, BigDecimal newAmount);
    void expenseUnlinkedFromGoal(UUID userId, UUID expenseId, UUID savingsGoalId, BigDecimal amount);
}
