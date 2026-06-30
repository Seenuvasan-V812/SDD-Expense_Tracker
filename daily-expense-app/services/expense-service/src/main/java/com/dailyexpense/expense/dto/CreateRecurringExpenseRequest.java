package com.dailyexpense.expense.dto;

import com.dailyexpense.expense.domain.RecurringFrequency;
import com.dailyexpense.expense.domain.PaymentMethod;
import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

public record CreateRecurringExpenseRequest(
    @NotNull @Valid MoneyDto amount,
    @NotNull UUID categoryId,
    @NotNull PaymentMethod paymentMethod,
    @NotNull RecurringFrequency frequency,
    @NotNull LocalDate anchorDate,
    String description,
    String merchant,
    String notes,
    LocalDate endDate,
    Integer maxOccurrences,
    Set<UUID> tagIds
) {}
