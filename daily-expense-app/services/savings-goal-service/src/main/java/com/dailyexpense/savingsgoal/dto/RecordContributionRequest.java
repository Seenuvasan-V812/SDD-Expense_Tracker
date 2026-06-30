package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.shared.money.MoneyDto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record RecordContributionRequest(
    @NotNull @Valid MoneyDto amount,
    @NotNull LocalDate date
) {}
