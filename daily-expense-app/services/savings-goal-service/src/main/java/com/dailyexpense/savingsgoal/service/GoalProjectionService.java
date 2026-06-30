package com.dailyexpense.savingsgoal.service;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.UUID;

/**
 * T080 — Computes projectedCompletionDate based on average monthly contribution rate.
 * Returns null for PAUSED and COMPLETED goals (undefined/already done).
 */
@Service
public class GoalProjectionService {

    private final ContributionEntryRepository entryRepository;

    public GoalProjectionService(ContributionEntryRepository entryRepository) {
        this.entryRepository = entryRepository;
    }

    @Transactional(readOnly = true)
    public LocalDate project(SavingsGoal goal) {
        if (goal.getStatus() == GoalStatus.PAUSED || goal.getStatus() == GoalStatus.COMPLETED) {
            return null;
        }

        BigDecimal remaining = goal.getTargetAmount()
            .subtract(goal.getTotalContributed());

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return LocalDate.now();
        }

        BigDecimal avgMonthly = computeAverageMonthlyRate(goal.getId());
        if (avgMonthly == null || avgMonthly.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }

        // months until completion
        BigDecimal months = remaining.divide(avgMonthly, 0, RoundingMode.CEILING);
        return LocalDate.now().plusMonths(months.longValue());
    }

    private BigDecimal computeAverageMonthlyRate(UUID goalId) {
        // Sum all contributions; divide by months since earliest entry
        // If no entries, return null
        try {
            return entryRepository.computeAverageMonthlyRate(goalId);
        } catch (Exception e) {
            return null;
        }
    }
}
