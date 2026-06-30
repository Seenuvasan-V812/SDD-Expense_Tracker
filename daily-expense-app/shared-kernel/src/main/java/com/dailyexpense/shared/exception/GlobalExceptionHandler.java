package com.dailyexpense.shared.exception;

import com.dailyexpense.shared.api.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Uniform exception → HTTP mapping. Produces ErrorResponse with 0 PII in message (API-3 / CQ-13).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getField)
            .sorted()
            .collect(Collectors.joining(", "));
        return response(HttpStatus.BAD_REQUEST, "Bad Request",
            "Validation failed for fields: " + fields, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request",
            "Required parameter '" + ex.getParameterName() + "' is missing", req);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Malformed request body", req);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(
            IllegalArgumentException ex, HttpServletRequest req) {
        return response(HttpStatus.BAD_REQUEST, "Bad Request", "Invalid argument", req);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthentication(
            AuthenticationException ex, HttpServletRequest req) {
        return response(HttpStatus.UNAUTHORIZED, "Unauthorized", "Authentication required", req);
    }

    @ExceptionHandler({AccessDeniedException.class, ForbiddenOwnershipException.class})
    public ResponseEntity<ErrorResponse> handleForbidden(
            RuntimeException ex, HttpServletRequest req) {
        return response(HttpStatus.FORBIDDEN, "Forbidden", "Access denied", req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            ResourceNotFoundException ex, HttpServletRequest req) {
        return response(HttpStatus.NOT_FOUND, "Not Found", ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(
            BusinessConflictException ex, HttpServletRequest req) {
        return response(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), req);
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimit(
            RateLimitExceededException ex, HttpServletRequest req) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", String.valueOf(ex.getRetryAfterSeconds()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .headers(headers)
            .body(ErrorResponse.of(429, "Too Many Requests", "Too many requests", req.getRequestURI(), traceId()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest req) {
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "An unexpected error occurred", req);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> response(HttpStatus status, String error, String message, HttpServletRequest req) {
        return ResponseEntity.status(status)
            .body(ErrorResponse.of(status.value(), error, message, req.getRequestURI(), traceId()));
    }

    private String traceId() {
        String id = MDC.get("traceId");
        return id != null ? id : "unknown";
    }
}
