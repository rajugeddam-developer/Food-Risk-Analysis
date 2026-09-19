package com.foodrisk.exception;

/**
 * Milestone M13: Standardized Application Error Categories.
 *
 * Defines machine-readable error codes returned in the ErrorResponse envelope.
 */
public enum ErrorCategory {
    INVALID_REQUEST,
    INVALID_IMAGE,
    IMAGE_TOO_LARGE,
    UNSUPPORTED_IMAGE_FORMAT,
    OCR_FAILED,
    OCR_LOW_CONFIDENCE,
    AI_SERVICE_UNAVAILABLE,
    AI_SERVICE_TIMEOUT,
    AI_RESPONSE_INVALID,
    FOOD_CATEGORY_UNKNOWN,
    ANALYSIS_NOT_FOUND,
    ANALYSIS_EXPIRED,
    ANALYSIS_TIMEOUT,
    ANALYSIS_FAILED,
    RATE_LIMIT_EXCEEDED,
    DATABASE_UNAVAILABLE,
    CACHE_UNAVAILABLE,
    INTERNAL_ERROR
}
