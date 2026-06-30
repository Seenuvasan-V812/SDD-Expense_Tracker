package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.savingsgoal.domain.ContributionSource;
import com.dailyexpense.shared.money.MoneyDto;

import java.time.LocalDate;
import java.util.UUID;

public record ContributionEntryResponse(
    UUID contributionEntryId,
    UUID expenseId,
    MoneyDto amount,
    LocalDate date,
    ContributionSource source
) {}
