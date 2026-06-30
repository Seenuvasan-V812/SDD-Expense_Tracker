package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record CreateSavingsGoalRequest(
    @NotBlank String name,
    @NotNull @Valid MoneyDto targetAmount,
    LocalDate targetDate,
    String description,
    String icon,
    String color
) {}
