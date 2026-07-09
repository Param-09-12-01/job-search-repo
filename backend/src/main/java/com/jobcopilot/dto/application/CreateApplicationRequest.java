package com.jobcopilot.dto.application;

import com.jobcopilot.entity.enums.ApplicationMethod;
import jakarta.validation.constraints.NotNull;

/**
 * Request to create an application (move a posting into the pipeline).
 */
public record CreateApplicationRequest(
        @NotNull(message = "postingId is required") Long postingId,
        String notes,
        ApplicationMethod method
) {
}
