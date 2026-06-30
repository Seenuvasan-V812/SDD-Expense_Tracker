package com.dailyexpense.user.dto;

import jakarta.validation.constraints.NotBlank;

public record RefreshRequest(
    @NotBlank String refreshToken
) {}
