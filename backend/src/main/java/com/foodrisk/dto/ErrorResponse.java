package com.foodrisk.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Milestone M13 Standardized Error Response Envelope.
 *
 * Provides structured, secure error details across all public REST endpoints:
 * - Sanitized message
 * - Machine-readable error code (from ErrorCategory)
 * - HTTP status code and standard reason phrase
 * - Correlation requestId for troubleshooting without leaking internals
 * - Request URI path
 * - Optional validation field errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId,
        Map<String, String> errors
) {
    public ErrorResponse(String message) {
        this(Instant.now(), 400, "BAD_REQUEST", "INVALID_REQUEST", message, null, null, null);
    }

    public ErrorResponse(String message, Map<String, String> errors) {
        this(Instant.now(), 400, "BAD_REQUEST", "INVALID_REQUEST", message, null, null, errors);
    }

    public ErrorResponse(String message, Map<String, String> errors, Instant timestamp) {
        this(timestamp != null ? timestamp : Instant.now(), 400, "BAD_REQUEST", "INVALID_REQUEST", message, null, null, errors);
    }

    public ErrorResponse(int status, String error, String code, String message, String path, String requestId) {
        this(Instant.now(), status, error, code, message, path, requestId, null);
    }
}
