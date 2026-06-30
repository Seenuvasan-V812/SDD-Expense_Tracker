package com.dailyexpense.budget.dto;

import jakarta.validation.constraints.NotNull;

public record ActivationRequest(@NotNull Boolean active) {}
