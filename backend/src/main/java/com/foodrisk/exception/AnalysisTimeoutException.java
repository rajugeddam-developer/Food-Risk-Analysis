package com.foodrisk.exception;

/**
 * Milestone M13: Thrown when food analysis orchestration exceeds the configured execution timeout.
 */
public class AnalysisTimeoutException extends RuntimeException {

    private final ErrorCategory category;

    public AnalysisTimeoutException(String message) {
        super(message);
        this.category = ErrorCategory.ANALYSIS_TIMEOUT;
    }

    public AnalysisTimeoutException(String message, Throwable cause) {
        super(message, cause);
        this.category = ErrorCategory.ANALYSIS_TIMEOUT;
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
