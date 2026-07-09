package com.jobcopilot.controller;

import com.jobcopilot.dto.settings.SettingsResponse;
import com.jobcopilot.dto.settings.UpdateSettingsRequest;
import com.jobcopilot.security.CurrentUserProvider;
import com.jobcopilot.service.AuditService;
import com.jobcopilot.service.SettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin settings endpoints. Reads mask secrets; writes are restricted to ADMIN and audited.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Manage API keys, scheduler, notifications and paths")
public class SettingsController {

    private final SettingsService settingsService;
    private final AuditService auditService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "Get all settings (secrets masked)")
    public ResponseEntity<SettingsResponse> get() {
        return ResponseEntity.ok(settingsService.getSettings());
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update settings")
    public ResponseEntity<SettingsResponse> update(@Valid @RequestBody UpdateSettingsRequest request) {
        SettingsResponse response = settingsService.updateSettings(request);
        auditService.record(currentUserProvider.getUsernameOrSystem(), "SETTINGS_UPDATE",
                "Keys: " + String.join(",", request.values().keySet()), null);
        return ResponseEntity.ok(response);
    }
}
