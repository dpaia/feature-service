package com.sivalabs.ft.features.domain.models;

/**
 * Enumeration of error types for tracking failures in usage event processing.
 */
public enum ErrorType {
    /**
     * Validation errors - invalid enum values, malformed JSON, missing required fields
     */
    VALIDATION_ERROR,

    /**
     * Database errors - constraint violations, unique violations, FK violations
     */
    DATABASE_ERROR,

    /**
     * Processing errors - NPE, parsing errors, business logic failures
     */
    PROCESSING_ERROR,

    /**
     * Permission errors - unauthorized access attempts
     */
    PERMISSION_ERROR
}
