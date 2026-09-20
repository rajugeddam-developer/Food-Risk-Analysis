package com.foodrisk.exception;

/**
 * Thrown when AI normalization fails or produces invalid food schema output.
 */
public class NormalizationException extends RuntimeException {
    private final String errorCode;

    public NormalizationException(String message) {
        super(message);
        this.errorCode = "NORMALIZATION_FAILED";
    }

    public NormalizationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "NORMALIZATION_FAILED";
    }

    public NormalizationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public NormalizationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
