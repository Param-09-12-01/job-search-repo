package com.jobcopilot.dto.scheduler;

import com.jobcopilot.entity.enums.SchedulerRunStatus;

import java.time.LocalDateTime;

/**
 * Scheduler run log representation returned to clients.
 */
public record SchedulerLogResponse(
        Long id,
        SchedulerRunStatus status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        int fetchedCount,
        int newCount,
        int duplicateCount,
        int notifiedCount,
        String message
) {
}
