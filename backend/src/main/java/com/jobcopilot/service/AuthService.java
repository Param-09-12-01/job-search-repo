package com.jobcopilot.service;

import com.jobcopilot.dto.auth.AuthResponse;
import com.jobcopilot.dto.auth.LoginRequest;
import com.jobcopilot.dto.auth.RefreshRequest;
import com.jobcopilot.exception.BadRequestException;
import com.jobcopilot.security.AppUserDetails;
import com.jobcopilot.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Handles authentication: username/password login and refresh-token exchange.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUserDetails principal = (AppUserDetails) authentication.getPrincipal();
        String role = principal.getUser().getRole().name();
        return buildResponse(principal.getUsername(), role);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!tokenProvider.isValid(token) || !tokenProvider.isRefreshToken(token)) {
            throw new BadRequestException("Invalid or expired refresh token");
        }
        String username = tokenProvider.extractUsername(token);
        String role = tokenProvider.extractRole(token);
        return buildResponse(username, role);
    }

    private AuthResponse buildResponse(String username, String role) {
        String accessToken = tokenProvider.generateAccessToken(username, role);
        String refreshToken = tokenProvider.generateRefreshToken(username, role);
        return new AuthResponse(accessToken, refreshToken, "Bearer",
                tokenProvider.getAccessTtlSeconds(), username, role);
    }
}
