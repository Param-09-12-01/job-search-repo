package com.jobcopilot.dto.posting;

import jakarta.validation.constraints.Size;

/**
 * Optional reason attached when dismissing a posting.
 */
public record DismissJobRequest(
        @Size(max = 255, message = "reason must be at most 255 characters") String reason
) {
}
