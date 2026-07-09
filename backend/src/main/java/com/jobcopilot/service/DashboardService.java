package com.jobcopilot.service;

import com.jobcopilot.dto.dashboard.DashboardResponse;
import com.jobcopilot.dto.scheduler.SchedulerLogResponse;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.mapper.SchedulerLogMapper;
import com.jobcopilot.repository.ApplicationRepository;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.repository.SavedJobRepository;
import com.jobcopilot.repository.SchedulerLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.Map;

/**
 * Aggregates dashboard data: statistic cards, recent notifications, and recent scheduler runs.
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PostingRepository postingRepository;
    private final SavedJobRepository savedJobRepository;
    private final ApplicationRepository applicationRepository;
    private final SchedulerLogRepository schedulerLogRepository;
    private final SchedulerLogMapper schedulerLogMapper;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        Map<ApplicationStatus, Long> counts = new EnumMap<>(ApplicationStatus.class);
        applicationRepository.countGroupedByStatus()
                .forEach(row -> counts.put(row.getStatus(), row.getCount()));

        long totalPostings = postingRepository.count();
        long appliedJobs = counts.getOrDefault(ApplicationStatus.APPLIED, 0L);
        long saved = savedJobRepository.count();

        DashboardResponse.Stats stats = new DashboardResponse.Stats(
                totalPostings - appliedJobs,                       // "new" = not yet applied
                saved,
                appliedJobs,
                counts.getOrDefault(ApplicationStatus.INTERVIEW, 0L),
                counts.getOrDefault(ApplicationStatus.REJECTED, 0L),
                counts.getOrDefault(ApplicationStatus.OFFER, 0L),
                totalPostings
        );

        var recentRuns = schedulerLogRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(schedulerLogMapper::toResponse)
                .toList();

        return new DashboardResponse(stats, notificationService.getRecent(), recentRuns);
    }

    @Transactional(readOnly = true)
    public java.util.List<SchedulerLogResponse> getRecentRuns() {
        return schedulerLogRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(schedulerLogMapper::toResponse)
                .toList();
    }
}
