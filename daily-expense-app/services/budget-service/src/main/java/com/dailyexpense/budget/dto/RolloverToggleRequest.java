package com.dailyexpense.budget.dto;

import jakarta.validation.constraints.NotNull;

public record RolloverToggleRequest(@NotNull Boolean rolloverEnabled) {}
