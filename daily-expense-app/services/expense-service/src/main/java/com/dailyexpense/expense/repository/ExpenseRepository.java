package com.dailyexpense.expense.repository;

import com.dailyexpense.expense.domain.Expense;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;
import java.util.stream.Stream;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, UUID>, JpaSpecificationExecutor<Expense> {

    /**
     * T064 — Idempotency: skip if occurrence already exists for this template + date.
     */
    boolean existsByRecurringExpenseIdAndExpenseDate(UUID recurringExpenseId, LocalDate expenseDate);

    /**
     * T066 — Streaming export (CQ-10: no full in-memory load).
     * fetchSize=50 keeps memory bounded while streaming cursor from PostgreSQL.
     */
    @Transactional(readOnly = true)
    @QueryHints(@QueryHint(name = "org.hibernate.fetchSize", value = "50"))
    @Query("""
        SELECT e FROM Expense e
        WHERE e.userId = :userId
          AND e.expenseDate >= :from
          AND e.expenseDate <= :to
        ORDER BY e.expenseDate ASC
        """)
    Stream<Expense> streamForExport(@Param("userId") UUID userId,
                                    @Param("from") LocalDate from,
                                    @Param("to") LocalDate to);
}
