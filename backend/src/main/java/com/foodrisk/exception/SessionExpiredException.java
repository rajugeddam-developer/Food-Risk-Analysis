package com.foodrisk.exception;

/**
 * Thrown when an analysis session has expired its TTL (HTTP 410 GONE).
 */
public class SessionExpiredException extends RuntimeException {
    public SessionExpiredException(String message) {
        super(message);
    }
}
