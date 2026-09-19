package com.foodrisk.exception;

/**
 * Thrown when an analysis stage (e.g. classification or risk engine) is invoked
 * before preceding prerequisite stages (e.g. M6 normalization) have completed (HTTP 409 CONFLICT).
 */
public class SessionNotReadyException extends RuntimeException {
    public SessionNotReadyException(String message) {
        super(message);
    }
}
