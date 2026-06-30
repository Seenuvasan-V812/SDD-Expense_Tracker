package com.dailyexpense.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 100) String fullName,
    @Size(max = 50) String timezone,
    @Size(max = 10) String locale,
    boolean weeklyDigestEnabled
) {}
