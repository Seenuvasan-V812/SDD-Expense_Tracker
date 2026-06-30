package com.dailyexpense.shared.exception;

/**
 * Thrown when a business rule prevents an operation (duplicate, illegal state transition, etc.).
 * Maps to HTTP 409.
 */
public class BusinessConflictException extends RuntimeException {

    public BusinessConflictException(String message) {
        super(message);
    }
}
