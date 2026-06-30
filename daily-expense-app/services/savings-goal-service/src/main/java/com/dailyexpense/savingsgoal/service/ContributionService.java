package com.dailyexpense.savingsgoal.service;

import com.dailyexpense.savingsgoal.domain.ContributionEntry;
import com.dailyexpense.savingsgoal.domain.ContributionSource;
import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.dto.ContributionEntryResponse;
import com.dailyexpense.savingsgoal.dto.RecordContributionRequest;
import com.dailyexpense.savingsgoal.port.ContributionPort;
import com.dailyexpense.savingsgoal.repository.ContributionEntryRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.exception.BusinessConflictException;
import com.dailyexpense.shared.money.MoneyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * T074 — Primary contribution flow: creates backing Expense via ContributionPort (no expense_db SQL, AL-1)
 * then inserts ContributionEntry source=GOAL_SCREEN.
 * T075 — GET /{id}/contributions history.
 */
@Service
public class ContributionService {

    private static final Logger log = LoggerFactory.getLogger(ContributionService.class);

    private final ContributionEntryRepository entryRepository;
    private final ContributionPort contributionPort;
    private final SavingsGoalService goalService;
    private final GoalLifecycleService lifecycleService;

    public ContributionService(ContributionEntryRepository entryRepository,
                                ContributionPort contributionPort,
                                SavingsGoalService goalService,
                                GoalLifecycleService lifecycleService) {
        this.entryRepository = entryRepository;
        this.contributionPort = contributionPort;
        this.goalService = goalService;
        this.lifecycleService = lifecycleService;
    }

    // T074 — primary flow
    @Transactional
    public ContributionEntry recordPrimary(UUID goalId, UUID userId,
                                           RecordContributionRequest request,
                                           String bearerToken) {
        validatePositiveAmount(request.amount().amount());

        SavingsGoal goal = goalService.findOwnedGoal(goalId, userId);
        if (goal.getStatus() == GoalStatus.COMPLETED || goal.getStatus() == GoalStatus.ABANDONED) {
            throw new BusinessConflictException(
                "Cannot contribute to a " + goal.getStatus() + " goal");
        }

        // AL-1: no expense_db SQL — call expense-service via port
        UUID expenseId = contributionPort.createBackingExpense(
            userId, request.amount().amount(), request.date(), goalId, bearerToken);

        ContributionEntry entry = new ContributionEntry();
        entry.setId(UUID.randomUUID());
        entry.setSavingsGoalId(goalId);
        entry.setUserId(userId);
        entry.setExpenseId(expenseId);
        entry.setAmount(request.amount().amount());
        entry.setCurrency("INR");
        entry.setEntryDate(request.date());
        entry.setSource(ContributionSource.GOAL_SCREEN);
        entry.setCreatedAt(Instant.now());
        entry.setUpdatedAt(Instant.now());

        entryRepository.save(entry);
        recomputeTotal(goal);

        log.info("ContributionEntry created for goal={} expense={} user={}", goalId, expenseId, userId);
        return entry;
    }

    // T075 — contribution history (paginated)
    @Transactional(readOnly = true)
    public PageResponse<ContributionEntryResponse> listHistory(UUID goalId, UUID userId,
                                                                int page, int size) {
        goalService.findOwnedGoal(goalId, userId);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "entryDate"));
        Page<ContributionEntry> entries = entryRepository.findBySavingsGoalId(goalId, pageRequest);
        return PageResponse.ofSpringPage(entries.map(this::toResponse));
    }

    private void recomputeTotal(SavingsGoal goal) {
        BigDecimal total = entryRepository.sumAmountByGoalId(goal.getId());
        goal.setTotalContributed(total != null ? total : BigDecimal.ZERO);
        goalService.saveGoal(goal);
        lifecycleService.checkAutoComplete(goal.getId(), goal.getUserId());
    }

    public ContributionEntryResponse toResponse(ContributionEntry entry) {
        return new ContributionEntryResponse(
            entry.getId(),
            entry.getExpenseId(),
            MoneyDto.ofInr(entry.getAmount()),
            entry.getEntryDate(),
            entry.getSource()
        );
    }

    private void validatePositiveAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Contribution amount must be > 0");
        }
    }
}
