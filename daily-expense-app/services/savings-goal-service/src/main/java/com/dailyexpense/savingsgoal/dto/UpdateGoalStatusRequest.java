package com.dailyexpense.savingsgoal.dto;

import com.dailyexpense.savingsgoal.domain.GoalStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateGoalStatusRequest(
    @NotNull GoalStatus status
) {}
