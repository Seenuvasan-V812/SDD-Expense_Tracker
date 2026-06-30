package com.dailyexpense.budget.dto;

import com.dailyexpense.budget.domain.Budget;
import com.dailyexpense.budget.domain.BudgetScope;
import com.dailyexpense.budget.domain.PeriodType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record BudgetResponse(
    UUID id,
    UUID userId,
    BudgetScope scope,
    UUID categoryId,
    BigDecimal budgetLimit,
    String currency,
    PeriodType periodType,
    boolean active,
    boolean rolloverEnabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static BudgetResponse from(Budget b) {
        return new BudgetResponse(
            b.getId(), b.getUserId(), b.getScope(), b.getCategoryId(),
            b.getBudgetLimit(), b.getCurrency(), b.getPeriodType(),
            b.isActive(), b.isRolloverEnabled(), b.getCreatedAt(), b.getUpdatedAt()
        );
    }
}
