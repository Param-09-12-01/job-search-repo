package com.jobcopilot.dto.application;

import com.jobcopilot.entity.enums.ApplicationMethod;
import com.jobcopilot.entity.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;

/**
 * Request to update an application's status (Kanban move), notes, or method.
 */
public record UpdateApplicationRequest(
        @NotNull(message = "status is required") ApplicationStatus status,
        String notes,
        ApplicationMethod method
) {
}
