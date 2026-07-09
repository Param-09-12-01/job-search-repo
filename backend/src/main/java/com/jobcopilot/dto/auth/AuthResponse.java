package com.jobcopilot.dto.auth;

/**
 * JWT bundle returned on successful authentication / refresh.
 */
public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds,
        String username,
        String role
) {
}
