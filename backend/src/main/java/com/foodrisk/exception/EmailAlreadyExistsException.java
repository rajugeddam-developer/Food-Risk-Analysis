package com.foodrisk.exception;

/**
 * Exception thrown when attempting to register with an email that is already registered.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}
