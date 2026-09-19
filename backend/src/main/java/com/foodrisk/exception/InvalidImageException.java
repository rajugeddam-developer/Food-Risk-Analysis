package com.foodrisk.exception;

/**
 * Thrown when an uploaded image fails format, magic byte, dimension, or decodability validation.
 */
public class InvalidImageException extends RuntimeException {

    private final ErrorCategory category;

    public InvalidImageException(String message) {
        this(ErrorCategory.INVALID_IMAGE, message);
    }

    public InvalidImageException(ErrorCategory category, String message) {
        super(message);
        this.category = category != null ? category : ErrorCategory.INVALID_IMAGE;
    }

    public InvalidImageException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category != null ? category : ErrorCategory.INVALID_IMAGE;
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
