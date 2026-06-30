package com.dailyexpense.budget.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetRequest(
    @NotNull @Pattern(regexp = "OVERALL|CATEGORY") String scope,
    UUID categoryId,
    @NotNull @DecimalMin(value = "0.01", message = "Budget limit must be greater than 0") BigDecimal budgetLimit,
    @NotNull @Pattern(regexp = "WEEKLY|MONTHLY") String periodType,
    boolean rolloverEnabled
) {}
