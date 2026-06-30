package com.dailyexpense.expense.dto;

import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * T054 — Create expense request DTO.
 * @NotNull fields produce 400 naming the field via GlobalExceptionHandler (MethodArgumentNotValidException).
 * amount>0 validated in ExpenseService (IllegalArgumentException→400).
 */
public record CreateExpenseRequest(
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
