package com.dailyexpense.shared.exception;

/**
 * Thrown when a caller attempts to read or mutate a resource they do not own.
 * Maps to HTTP 403 — NEVER 404 (INV-1 / SEC-3).
 */
public class ForbiddenOwnershipException extends RuntimeException {

    public ForbiddenOwnershipException(Object resourceId) {
        super("Access denied"); // no resource ID in message to avoid enumeration (CQ-13)
    }

    public ForbiddenOwnershipException() {
        super("Access denied");
    }
}
