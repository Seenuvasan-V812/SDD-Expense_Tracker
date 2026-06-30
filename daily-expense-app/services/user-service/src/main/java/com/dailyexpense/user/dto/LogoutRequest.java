package com.dailyexpense.user.dto;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequest(
    @NotBlank String refreshToken
) {}
