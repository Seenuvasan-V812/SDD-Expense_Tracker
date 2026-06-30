package com.dailyexpense.shared.exception;

/**
 * Thrown when a resource genuinely does not exist (as opposed to an ownership failure → 403).
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(resourceType + " not found");
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
