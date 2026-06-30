package com.dailyexpense.savingsgoal.service;

import com.dailyexpense.savingsgoal.domain.ContributionEntry;
import com.dailyexpense.savingsgoal.domain.ContributionSource;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * T077 — Reconciles contribution totals when expense-service emits adjustment/unlink/delete events.
 * All methods are idempotent via the processed_events guard called in ExpenseEventConsumer.
 * No expense_db SQL (AL-1).
 */
@Service
public class ContributionReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ContributionReconciliationService.class);

    private final ContributionEntryRepository entryRepository;
    private final SavingsGoalRepository goalRepository;
    private final GoalLifecycleService lifecycleService;

    public ContributionReconciliationService(ContributionEntryRepository entryRepository,
                                              SavingsGoalRepository goalRepository,
                                              GoalLifecycleService lifecycleService) {
        this.entryRepository = entryRepository;
        this.goalRepository = goalRepository;
        this.lifecycleService = lifecycleService;
    }

    // T076 — secondary flow: ExpenseLinkedToSavingsGoalEvent → create entry source=LINKED_EXPENSE
    @Transactional
    public void onExpenseLinked(UUID goalId, UUID userId, UUID expenseId,
                                BigDecimal amount, LocalDate date) {
        Optional<ContributionEntry> existing = entryRepository.findBySavingsGoalIdAndExpenseId(goalId, expenseId);
        if (existing.isPresent()) {
            log.warn("Duplicate link event ignored: goal={} expense={}", goalId, expenseId);
            return;
        }

        SavingsGoal goal = goalRepository.findById(goalId).orElse(null);
        if (goal == null || !goal.getUserId().equals(userId)) return;

        ContributionEntry entry = new ContributionEntry();
        entry.setId(UUID.randomUUID());
        entry.setSavingsGoalId(goalId);
        entry.setUserId(userId);
        entry.setExpenseId(expenseId);
        entry.setAmount(amount);
        entry.setCurrency("INR");
        entry.setEntryDate(date != null ? date : LocalDate.now());
        entry.setSource(ContributionSource.LINKED_EXPENSE);
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());

        entryRepository.save(entry);
        recomputeAndCheck(goal);
        log.info("LINKED_EXPENSE entry created: goal={} expense={}", goalId, expenseId);
    }

    // T077 — amount adjusted: update entry + recompute total
    @Transactional
    public void onExpenseAmountAdjusted(UUID goalId, UUID userId, UUID expenseId, BigDecimal newAmount) {
        entryRepository.findBySavingsGoalIdAndExpenseId(goalId, expenseId).ifPresent(entry -> {
            entry.setAmount(newAmount);
            entry.setUpdatedAt(Instant.now());
            entryRepository.save(entry);

            SavingsGoal goal = goalRepository.findById(goalId).orElse(null);
            if (goal != null) recomputeAndCheck(goal);
        });
    }

    // T077 — unlinked or deleted: remove entry + recompute total
    @Transactional
    public void onExpenseUnlinkedOrDeleted(UUID goalId, UUID userId, UUID expenseId) {
        entryRepository.findBySavingsGoalIdAndExpenseId(goalId, expenseId).ifPresent(entry -> {
            entryRepository.delete(entry);

            SavingsGoal goal = goalRepository.findById(goalId).orElse(null);
            if (goal != null) {
                recomputeAndCheck(goal);
                lifecycleService.reopenIfCompleted(goalId, userId);
            }
        });
    }

    private void recomputeAndCheck(SavingsGoal goal) {
        BigDecimal total = entryRepository.sumAmountByGoalId(goal.getId());
        goal.setTotalContributed(total != null ? total : BigDecimal.ZERO);
        goal.setUpdatedAt(Instant.now());
        goalRepository.save(goal);
        lifecycleService.checkAutoComplete(goal.getId(), goal.getUserId());
    }
}
