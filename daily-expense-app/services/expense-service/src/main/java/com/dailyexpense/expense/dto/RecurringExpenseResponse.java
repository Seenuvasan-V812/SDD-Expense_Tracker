package com.dailyexpense.expense.dto;

import com.dailyexpense.shared.money.MoneyDto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record RecurringExpenseResponse(
    UUID recurringExpenseId,
    MoneyDto amount,
    UUID categoryId,
    String paymentMethod,
    String frequency,
    LocalDate anchorDate,
    LocalDate endDate,
    Integer maxOccurrences,
    int generatedCount,
    LocalDate nextRunDate,
    String description,
    String merchant,
    String notes,
    Set<UUID> tagIds,
    Instant createdAt,
    Instant updatedAt
) {}
