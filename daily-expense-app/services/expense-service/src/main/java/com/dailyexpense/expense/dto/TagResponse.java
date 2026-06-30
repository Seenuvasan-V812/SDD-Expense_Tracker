package com.dailyexpense.expense.dto;

import java.time.Instant;
import java.util.UUID;

public record TagResponse(UUID tagId, UUID userId, String name, Instant createdAt) {}
