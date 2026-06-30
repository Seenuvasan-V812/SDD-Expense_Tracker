package com.dailyexpense.expense.dto;

import com.dailyexpense.shared.money.MoneyDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * T054/T055 — Expense response DTO (AL-4: entity never serialized directly).
 */
public record ExpenseResponse(
    UUID expenseId,
    MoneyDto amount,
    LocalDate date,
    UUID categoryId,
    String paymentMethod,
    String description,
    String merchant,
    String notes,
    Set<UUID> tags,
    boolean hasReceipt,
    UUID savingsGoalId,
    UUID recurringExpenseId,
    Instant createdAt,
    Instant updatedAt
) {}
