package com.dailyexpense.budget.dto;

import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record UpdateBudgetRequest(
    @DecimalMin(value = "0.01", message = "Budget limit must be greater than 0") BigDecimal budgetLimit
) {}
