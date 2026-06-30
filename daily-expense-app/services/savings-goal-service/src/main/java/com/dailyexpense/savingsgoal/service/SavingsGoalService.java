package com.dailyexpense.savingsgoal.service;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.savingsgoal.domain.SavingsGoal;
import com.dailyexpense.savingsgoal.dto.CreateSavingsGoalRequest;
import com.dailyexpense.savingsgoal.dto.SavingsGoalResponse;
import com.dailyexpense.savingsgoal.dto.UpdateSavingsGoalRequest;
import com.dailyexpense.savingsgoal.repository.SavingsGoalRepository;
import com.dailyexpense.shared.api.PageResponse;
import com.dailyexpense.shared.exception.ForbiddenOwnershipException;
import com.dailyexpense.shared.exception.ResourceNotFoundException;
import com.dailyexpense.shared.money.MoneyDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * T072 — Goal CRUD. 403-never-404 (INV-1). Big Decimal for all money (DB-5).
 * Outbox write for SavingsGoalDeletedEvent is in GoalLifecycleService (T081).
 */
@Service
public class SavingsGoalService {

    private static final Logger log = LoggerFactory.getLogger(SavingsGoalService.class);

    private final SavingsGoalRepository goalRepository;

    public SavingsGoalService(SavingsGoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    @Transactional
    public SavingsGoal create(UUID userId, CreateSavingsGoalRequest request) {
        validatePositiveAmount(request.targetAmount().amount(), "targetAmount");

        SavingsGoal goal = new SavingsGoal();
        goal.setId(UUID.randomUUID());
        goal.setUserId(userId);
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount().amount());
        goal.setTargetDate(request.targetDate());
        goal.setDescription(request.description());
        goal.setIcon(request.icon());
        goal.setColor(request.color());
        goal.setStatus(GoalStatus.ACTIVE);
        goal.setTotalContributed(BigDecimal.ZERO);
        goal.setCreatedAt(Instant.now());
        goal.setUpdatedAt(Instant.now());

        SavingsGoal saved = goalRepository.save(goal);
        log.info("Created savingsGoal={} for user={}", saved.getId(), userId);
        return saved;
    }

    @Transactional(readOnly = true)
    public PageResponse<SavingsGoalResponse> list(UUID userId, GoalStatus status, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<SavingsGoal> goalPage = (status != null)
            ? goalRepository.findByUserIdAndStatus(userId, status, pageRequest)
            : goalRepository.findByUserId(userId, pageRequest);

        return PageResponse.ofSpringPage(goalPage.map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public SavingsGoalResponse getDetail(UUID id, UUID userId) {
        SavingsGoal goal = findOwnedGoal(id, userId);
        return toResponse(goal);
    }

    @Transactional
    public SavingsGoalResponse update(UUID id, UUID userId, UpdateSavingsGoalRequest request) {
        SavingsGoal goal = findOwnedGoal(id, userId);

        if (request.name() != null) goal.setName(request.name());
        if (request.targetDate() != null) goal.setTargetDate(request.targetDate());
        if (request.description() != null) goal.setDescription(request.description());
        if (request.icon() != null) goal.setIcon(request.icon());
        if (request.color() != null) goal.setColor(request.color());

        if (request.targetAmount() != null) {
            validatePositiveAmount(request.targetAmount().amount(), "targetAmount");
            goal.setTargetAmount(request.targetAmount().amount());
        }

        goal.setUpdatedAt(Instant.now());
        return toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public Optional<SavingsGoal> findById(UUID id) {
        return goalRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<SavingsGoal> findOwnedOptional(UUID id, UUID userId) {
        return goalRepository.findByIdAndUserId(id, userId);
    }

    public SavingsGoal findOwnedGoal(UUID id, UUID userId) {
        return goalRepository.findById(id)
            .map(goal -> {
                if (!goal.getUserId().equals(userId)) {
                    throw new ForbiddenOwnershipException("Access denied to savings goal " + id);
                }
                return goal;
            })
            .orElseThrow(() -> new ResourceNotFoundException("Savings goal not found: " + id));
    }

    @Transactional
    public void saveGoal(SavingsGoal goal) {
        goal.setUpdatedAt(Instant.now());
        goalRepository.save(goal);
    }

    public SavingsGoalResponse toResponse(SavingsGoal goal) {
        BigDecimal remaining = goal.getTargetAmount()
            .subtract(goal.getTotalContributed())
            .max(BigDecimal.ZERO);

        double percent = 0.0;
        if (goal.getTargetAmount().compareTo(BigDecimal.ZERO) > 0) {
            percent = goal.getTotalContributed()
                .multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP)
                .doubleValue();
            percent = Math.min(percent, 100.0);
        }

        LocalDateTime createdAt = goal.getCreatedAt() != null
            ? LocalDateTime.ofInstant(goal.getCreatedAt(), ZoneOffset.UTC) : null;

        return new SavingsGoalResponse(
            goal.getId(),
            goal.getName(),
            MoneyDto.ofInr(goal.getTargetAmount()),
            goal.getTargetDate(),
            goal.getDescription(),
            goal.getStatus(),
            MoneyDto.ofInr(goal.getTotalContributed()),
            MoneyDto.ofInr(remaining),
            percent,
            null,
            goal.getIcon(),
            goal.getColor(),
            createdAt
        );
    }

    private void validatePositiveAmount(BigDecimal amount, String field) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
    }
}
