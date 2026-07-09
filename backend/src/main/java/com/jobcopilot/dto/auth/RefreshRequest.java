package com.jobcopilot.dto.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Request to exchange a refresh token for a fresh access token.
 */
public record RefreshRequest(
        @NotBlank(message = "refreshToken is required") String refreshToken
) {
}
