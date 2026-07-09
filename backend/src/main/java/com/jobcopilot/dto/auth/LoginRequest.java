package com.jobcopilot.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials submitted at login.
 */
public record LoginRequest(
        @NotBlank(message = "username is required") String username,
        @NotBlank(message = "password is required") String password
) {
}
