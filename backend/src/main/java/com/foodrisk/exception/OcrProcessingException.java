package com.foodrisk.exception;

/**
 * Milestone M13: Thrown when OCR processing fails, image is unreadable, or confidence is critically low.
 */
public class OcrProcessingException extends RuntimeException {

    private final ErrorCategory category;

    public OcrProcessingException(ErrorCategory category, String message) {
        super(message);
        this.category = category != null ? category : ErrorCategory.OCR_FAILED;
    }

    public OcrProcessingException(ErrorCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category != null ? category : ErrorCategory.OCR_FAILED;
    }

    public OcrProcessingException(String message) {
        this(ErrorCategory.OCR_FAILED, message);
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
