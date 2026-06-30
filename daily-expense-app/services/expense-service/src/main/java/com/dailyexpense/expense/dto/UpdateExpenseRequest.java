package com.dailyexpense.expense.dto;

import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * T056 — Update expense request DTO.
 * When savingsGoalId is set, service enforces EXP-INV-5 (categoryId must be Savings Category).
 */
public record UpdateExpenseRequest(
    @NotNull @Valid MoneyDto amount,
    @NotNull LocalDate date,
    @NotNull UUID categoryId,
    @NotNull PaymentMethod paymentMethod,
    String description,
    String merchant,
    String notes,
    Set<UUID> tagIds,
    UUID savingsGoalId
) {}
