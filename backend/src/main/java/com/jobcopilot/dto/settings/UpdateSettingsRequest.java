package com.jobcopilot.dto.settings;

import jakarta.validation.constraints.NotEmpty;

import java.util.Map;

/**
 * Bulk settings update from the admin panel. Only whitelisted keys are persisted.
 */
public record UpdateSettingsRequest(
        @NotEmpty(message = "values must not be empty") Map<String, String> values
) {
}
