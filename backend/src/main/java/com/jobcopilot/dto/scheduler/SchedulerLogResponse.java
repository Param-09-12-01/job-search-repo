package com.jobcopilot.dto.scheduler;

import com.jobcopilot.entity.enums.SchedulerRunStatus;

import java.time.Instant;

/**
 * Scheduler run log representation returned to clients.
 */
public record SchedulerLogResponse(
        Long id,
        SchedulerRunStatus status,
        Instant startedAt,
        Instant finishedAt,
        int fetchedCount,
        int newCount,
        int duplicateCount,
        int notifiedCount,
        String message
) {
}
