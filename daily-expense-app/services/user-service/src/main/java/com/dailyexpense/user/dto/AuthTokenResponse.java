package com.dailyexpense.user.dto;

public record AuthTokenResponse(
    String accessToken,
    String refreshToken,
    long expiresInSec
) {}
