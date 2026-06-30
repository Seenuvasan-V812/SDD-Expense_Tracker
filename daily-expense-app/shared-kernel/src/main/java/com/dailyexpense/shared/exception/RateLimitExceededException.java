package com.dailyexpense.shared.exception;

/**
 * Thrown when the per-IP or per-account request rate is exceeded.
 * Maps to HTTP 429 with a Retry-After header (SEC-4).
 */
public class RateLimitExceededException extends RuntimeException {

    private final int retryAfterSeconds;

    public RateLimitExceededException(int retryAfterSeconds) {
        super("Too many requests");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
