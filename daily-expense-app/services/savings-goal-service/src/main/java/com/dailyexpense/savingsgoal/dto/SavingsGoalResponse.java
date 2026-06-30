package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import com.dailyexpense.shared.money.MoneyDto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record SavingsGoalResponse(
    UUID savingsGoalId,
    String name,
    MoneyDto targetAmount,
    LocalDate targetDate,
    String description,
    GoalStatus status,
    MoneyDto totalContributed,
    MoneyDto remainingAmount,
    double percentAchieved,
    LocalDate projectedCompletionDate,
    String icon,
    String color,
    LocalDateTime createdAt
) {}
