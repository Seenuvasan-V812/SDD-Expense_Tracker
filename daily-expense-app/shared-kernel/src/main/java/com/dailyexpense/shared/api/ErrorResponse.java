package com.dailyexpense.shared.api;

import java.time.Instant;

/**
 * Uniform error envelope — API-3.
 * Serializes exactly: timestamp, status, error, message, path, traceId.
 * The {@code message} field MUST carry zero PII (CQ-13).
 */
public record ErrorResponse(
    Instant timestamp,
    int status,
    String error,
    String message,
    String path,
    String traceId
) {

    public static ErrorResponse of(int status, String error, String message, String path, String traceId) {
        return new ErrorResponse(Instant.now(), status, error, message, path, traceId);
    }
}
