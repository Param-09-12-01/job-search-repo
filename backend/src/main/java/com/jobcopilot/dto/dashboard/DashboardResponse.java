package com.jobcopilot.dto.dashboard;

import com.jobcopilot.dto.notification.NotificationResponse;
import com.jobcopilot.dto.scheduler.SchedulerLogResponse;

import java.util.List;

/**
 * Aggregated dashboard payload: statistic cards, recent notifications, and recent scheduler runs.
 */
public record DashboardResponse(
        Stats stats,
        List<NotificationResponse> recentNotifications,
        List<SchedulerLogResponse> recentSchedulerRuns
) {
    public record Stats(
            long newJobs,
            long savedJobs,
            long appliedJobs,
            long interview,
            long rejected,
            long offer,
            long totalPostings
    ) {
    }
}
