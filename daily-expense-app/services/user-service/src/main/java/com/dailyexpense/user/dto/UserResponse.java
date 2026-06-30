package com.dailyexpense.user.dto;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String fullName,
    String email,
    String status,
    String preferredCurrency,
    String timezone,
    String locale,
    boolean weeklyDigestEnabled,
    Instant createdAt
) {}
