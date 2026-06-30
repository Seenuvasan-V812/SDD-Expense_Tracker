package com.dailyexpense.expense.repository;

import com.dailyexpense.expense.domain.RecurringExpense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RecurringExpenseRepository extends JpaRepository<RecurringExpense, UUID> {

    Optional<RecurringExpense> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Find templates due for generation: next_run_date <= referenceDate,
     * not past end_date, not exhausted max_occurrences.
     */
    @Query("""
        SELECT r FROM RecurringExpense r
        WHERE r.nextRunDate <= :referenceDate
          AND (r.endDate IS NULL OR r.endDate >= :referenceDate)
          AND (r.maxOccurrences IS NULL OR r.generatedCount < r.maxOccurrences)
        """)
    List<RecurringExpense> findDueTemplates(@Param("referenceDate") LocalDate referenceDate);
}
