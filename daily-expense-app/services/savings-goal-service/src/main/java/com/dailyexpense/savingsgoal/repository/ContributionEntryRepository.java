package com.dailyexpense.savingsgoal.repository;

import com.dailyexpense.savingsgoal.domain.ContributionEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface ContributionEntryRepository extends JpaRepository<ContributionEntry, UUID> {

    Page<ContributionEntry> findBySavingsGoalId(UUID savingsGoalId, Pageable pageable);

    Optional<ContributionEntry> findBySavingsGoalIdAndExpenseId(UUID savingsGoalId, UUID expenseId);

    @Query("SELECT COALESCE(SUM(e.amount), 0) FROM ContributionEntry e WHERE e.savingsGoalId = :goalId")
    BigDecimal sumAmountByGoalId(@Param("goalId") UUID goalId);

    @Query(value = """
        SELECT CASE
          WHEN COUNT(*) = 0 THEN NULL
          WHEN EXTRACT(EPOCH FROM (MAX(entry_date) - MIN(entry_date))) < 86400 THEN SUM(amount)
          ELSE SUM(amount) / GREATEST(
               EXTRACT(EPOCH FROM (MAX(entry_date) - MIN(entry_date))) / 2592000.0, 1)
        END
        FROM contribution_entries WHERE savings_goal_id = :goalId
        """, nativeQuery = true)
    BigDecimal computeAverageMonthlyRate(@Param("goalId") UUID goalId);
}
