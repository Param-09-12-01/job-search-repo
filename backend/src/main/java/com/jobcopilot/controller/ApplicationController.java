package com.jobcopilot.controller;

import com.jobcopilot.dto.application.ApplicationResponse;
import com.jobcopilot.dto.application.CreateApplicationRequest;
import com.jobcopilot.dto.application.ManualApplicationRequest;
import com.jobcopilot.dto.application.UpdateApplicationRequest;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Application tracker endpoints, including the Kanban board grouped by status.
 */
@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@Tag(name = "Applications", description = "Track applications through the Kanban pipeline")
public class ApplicationController {

    private final ApplicationService applicationService;

    @GetMapping
    @Operation(summary = "List all applications")
    public ResponseEntity<List<ApplicationResponse>> getAll() {
        return ResponseEntity.ok(applicationService.getAll());
    }

    @GetMapping("/board")
    @Operation(summary = "Get applications grouped by Kanban column")
    public ResponseEntity<Map<ApplicationStatus, List<ApplicationResponse>>> getBoard() {
        return ResponseEntity.ok(applicationService.getBoard());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single application")
    public ResponseEntity<ApplicationResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(applicationService.getById(id));
    }

    @PostMapping("/manual")
    @Operation(summary = "Create a manual application (creates a posting + application in one call)")
    public ResponseEntity<ApplicationResponse> createManual(@Valid @RequestBody ManualApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.createManual(request));
    }

    @PostMapping
    @Operation(summary = "Create an application from a posting")
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationService.create(request));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update application status/notes/method (Kanban move)")
    public ResponseEntity<ApplicationResponse> update(@PathVariable Long id,
                                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return ResponseEntity.ok(applicationService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an application")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        applicationService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
