package com.jobcopilot.exception;

/**
 * Thrown when an operation conflicts with the current state (e.g. duplicate resource).
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
