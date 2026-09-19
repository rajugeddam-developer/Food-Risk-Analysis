package com.foodrisk.exception;

/**
 * Thrown when an uploaded image fails format, magic byte, dimension, or decodability validation.
 */
public class InvalidImageException extends RuntimeException {
    public InvalidImageException(String message) {
        super(message);
    }
}
