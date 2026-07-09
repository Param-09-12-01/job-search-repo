package com.jobcopilot.controller;

import com.jobcopilot.dto.automation.PrepareApplicationResponse;
import com.jobcopilot.service.PrepareApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prepare-application endpoint. Launches the browser and fills fields, then STOPS.
 * The final Apply button is always clicked manually by the user.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Prepare Application",
        description = "Fill application fields via Playwright. Never submits, never logs in.")
public class PrepareApplicationController {

    private final PrepareApplicationService prepareApplicationService;

    @PostMapping("/{id}/prepare")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Open browser and fill the application form for a posting (does NOT submit)")
    public ResponseEntity<PrepareApplicationResponse> prepare(@PathVariable Long id) {
        return ResponseEntity.ok(prepareApplicationService.prepare(id));
    }
}
