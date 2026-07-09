package com.jobcopilot.dto.posting;

import jakarta.validation.constraints.Size;

/**
 * Optional note attached when saving a posting.
 */
public record SaveJobRequest(
        @Size(max = 2000, message = "note must be at most 2000 characters") String note
) {
}
