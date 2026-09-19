package com.foodrisk.exception;

/**
 * Thrown when an uploaded image exceeds the allowed file size limit (HTTP 413).
 */
public class ImageSizeLimitExceededException extends RuntimeException {
    public ImageSizeLimitExceededException(String message) {
        super(message);
    }
}
