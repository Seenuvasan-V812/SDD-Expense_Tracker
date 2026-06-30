package com.dailyexpense.budget.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record BudgetStatusResponse(
    UUID budgetId,
    UUID ledgerId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal budgetLimit,
    BigDecimal carriedIn,
    BigDecimal effectiveLimit,
    BigDecimal spent,
    BigDecimal remaining,
    BigDecimal percentUsed,
    boolean firedEightyPercent,
    boolean firedExceeded
) {}
