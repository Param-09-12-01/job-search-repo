package com.jobcopilot.controller;

import com.jobcopilot.dto.auth.AuthResponse;
import com.jobcopilot.dto.auth.LoginRequest;
import com.jobcopilot.dto.auth.RefreshRequest;
import com.jobcopilot.security.CurrentUserProvider;
import com.jobcopilot.service.AuditService;
import com.jobcopilot.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints: login and refresh. These are the only public API routes.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login and token refresh")
public class AuthController {

    private final AuthService authService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/login")
    @Operation(summary = "Authenticate and receive access/refresh tokens")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request);
        auditService.record(request.username(), "LOGIN", "Successful login", clientIp(httpRequest));
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a new access token")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
