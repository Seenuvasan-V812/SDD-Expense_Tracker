package com.dailyexpense.user.dto;

import java.util.UUID;

public record DataExportResponse(
    UUID exportId,
    String status
) {}
