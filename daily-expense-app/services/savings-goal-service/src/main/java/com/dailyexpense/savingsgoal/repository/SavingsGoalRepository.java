package com.dailyexpense.savingsgoal.repository;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    Page<SavingsGoal> findByUserId(UUID userId, Pageable pageable);

    Page<SavingsGoal> findByUserIdAndStatus(UUID userId, GoalStatus status, Pageable pageable);

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Atomic conditional status transition: ACTIVE → COMPLETED.
     * Returns 1 if the goal was ACTIVE and is now COMPLETED; 0 otherwise (already COMPLETED or not found).
     * Used by SG-INV-6 double-record guard — ensures auto-complete event fires exactly once
     * even under concurrent reconcile calls.
     */
    @Modifying
    @Query("UPDATE SavingsGoal g SET g.status = :completed, g.updatedAt = :now " +
           "WHERE g.id = :id AND g.status = :active")
    int tryComplete(@Param("id") UUID id,
                   @Param("completed") GoalStatus completed,
                   @Param("active") GoalStatus active,
                   @Param("now") Instant now);
}
