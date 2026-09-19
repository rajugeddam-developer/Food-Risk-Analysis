package com.foodrisk.exception;

/**
 * Thrown when an analysis session ID or session token is not found in the database.
 */
public class SessionNotFoundException extends RuntimeException {
    public SessionNotFoundException(String message) {
        super(message);
    }
}
