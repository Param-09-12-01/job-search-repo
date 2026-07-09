package com.jobcopilot.dto.application;

import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.entity.enums.ApplicationMethod;
import com.jobcopilot.entity.enums.ApplicationStatus;

import java.time.LocalDateTime;

/**
 * Application representation returned to clients, including its posting.
 */
public record ApplicationResponse(
        Long id,
        PostingResponse posting,
        ApplicationStatus status,
        LocalDateTime appliedDate,
        String notes,
        ApplicationMethod method,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
