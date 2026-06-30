package com.dailyexpense.budget.repository;

import com.dailyexpense.budget.domain.Budget;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BudgetRepository extends JpaRepository<Budget, UUID> {

    Page<Budget> findByUserId(UUID userId, Pageable pageable);

    Page<Budget> findByUserIdAndActive(UUID userId, boolean active, Pageable pageable);

    Optional<Budget> findByIdAndUserId(UUID id, UUID userId);
}
