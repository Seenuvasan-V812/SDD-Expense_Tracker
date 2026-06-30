package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;

import java.time.LocalDate;

public record UpdateSavingsGoalRequest(
    String name,
    @Valid MoneyDto targetAmount,
    LocalDate targetDate,
    String description,
    String icon,
    String color
) {}
