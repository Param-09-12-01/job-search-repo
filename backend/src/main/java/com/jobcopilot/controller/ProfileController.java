package com.jobcopilot.controller;

import com.jobcopilot.dto.profile.ProfileRequest;
import com.jobcopilot.dto.profile.ProfileResponse;
import com.jobcopilot.service.ProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profile endpoints. The platform has a single profile; PUT is upsert semantics.
 */
@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Tag(name = "Profile", description = "Manage the user's job-search profile and preferences")
public class ProfileController {

    private final ProfileService profileService;

    @GetMapping
    @Operation(summary = "Get the current profile")
    public ResponseEntity<ProfileResponse> getProfile() {
        return ResponseEntity.ok(profileService.getProfile());
    }

    @PutMapping
    @Operation(summary = "Create or update the profile")
    public ResponseEntity<ProfileResponse> saveProfile(@Valid @RequestBody ProfileRequest request) {
        return ResponseEntity.ok(profileService.saveProfile(request));
    }
}
