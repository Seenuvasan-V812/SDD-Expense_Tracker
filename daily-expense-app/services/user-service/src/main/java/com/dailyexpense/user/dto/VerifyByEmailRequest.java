package com.dailyexpense.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record VerifyByEmailRequest(
    @NotBlank @Email String email
) {}
