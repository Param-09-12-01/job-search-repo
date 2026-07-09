package com.jobcopilot.exception;

/**
 * Thrown for invalid business operations that are not simple validation failures.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
