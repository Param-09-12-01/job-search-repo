package com.jobcopilot.exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Standard error payload returned by the {@link GlobalExceptionHandler}.
 */
@Data
@Builder
@AllArgsConstructor
public class ApiError {

    private LocalDateTime timestamp;
    private int status;
    private String error;
    private String message;
    private String path;
    private List<FieldValidationError> fieldErrors;

    @Data
    @AllArgsConstructor
    public static class FieldValidationError {
        private String field;
        private String message;
    }
}
