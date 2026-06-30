package com.dailyexpense.budget.repository;

import com.dailyexpense.budget.domain.BudgetPeriodLedger;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BudgetPeriodLedgerRepository extends JpaRepository<BudgetPeriodLedger, UUID> {

    Optional<BudgetPeriodLedger> findByBudgetIdAndPeriodStart(UUID budgetId, LocalDate periodStart);

    /** Active ledger: periodStart <= today <= periodEnd */
    @Query("SELECT l FROM BudgetPeriodLedger l WHERE l.budgetId = :budgetId " +
           "AND l.periodStart <= :today AND l.periodEnd >= :today")
    Optional<BudgetPeriodLedger> findActiveLedger(@Param("budgetId") UUID budgetId,
                                                   @Param("today") LocalDate today);

    /** All ledgers whose period has ended and not yet rolled over (for rollover scheduler). */
    @Query("SELECT l FROM BudgetPeriodLedger l WHERE l.periodEnd < :today " +
           "AND NOT EXISTS (" +
           "  SELECT n FROM BudgetPeriodLedger n WHERE n.budgetId = l.budgetId " +
           "  AND n.periodStart > l.periodStart" +
           ")")
    List<BudgetPeriodLedger> findExpiredWithoutSuccessor(@Param("today") LocalDate today);

    @Modifying
    @Query("UPDATE BudgetPeriodLedger l SET l.spent = l.spent + :delta, l.updatedAt = :now " +
           "WHERE l.id = :id")
    int addSpent(@Param("id") UUID id, @Param("delta") java.math.BigDecimal delta,
                 @Param("now") java.time.Instant now);
}
