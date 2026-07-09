package com.jobcopilot.dto.settings;

import java.util.Map;

/**
 * Settings snapshot returned to the admin panel. Secret values are masked.
 */
public record SettingsResponse(
        Map<String, String> values
) {
}
