package com.jobcopilot.exception;

/**
 * Thrown when an external job-provider integration fails (network, auth, or malformed response).
 * Never aborts a scheduler run: callers isolate failures per source.
 */
public class IntegrationException extends RuntimeException {

    public IntegrationException(String message) {
        super(message);
    }

    public IntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
