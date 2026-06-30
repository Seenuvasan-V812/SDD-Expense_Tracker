package com.dailyexpense.expense.repository;

import com.dailyexpense.expense.domain.Receipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {

    boolean existsByExpenseId(UUID expenseId);

    Optional<Receipt> findByExpenseId(UUID expenseId);

    @Query("SELECT r.expenseId FROM Receipt r WHERE r.expenseId IN :expenseIds")
    Set<UUID> findExpenseIdsWithReceiptsIn(Collection<UUID> expenseIds);
}
