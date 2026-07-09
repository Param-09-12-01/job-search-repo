package com.jobcopilot.scheduler;

import com.jobcopilot.entity.SchedulerLog;
import com.jobcopilot.entity.enums.SchedulerRunStatus;
import com.jobcopilot.repository.SchedulerLogRepository;
import com.jobcopilot.service.JobIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the ingestion pipeline and records a {@link SchedulerLog} for each run.
 * A guard prevents overlapping runs if a cycle takes longer than the interval.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobFetchRunner {

    private final JobIngestionService ingestionService;
    private final SchedulerLogRepository schedulerLogRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    /**
     * Execute one ingestion cycle with full logging. Safe to call from the cron scheduler or the
     * manual trigger endpoint. Returns the persisted log.
     */
    @Transactional
    public SchedulerLog runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Ingestion run skipped — a previous run is still in progress");
            SchedulerLog skipped = SchedulerLog.builder()
                    .status(SchedulerRunStatus.SUCCESS)
                    .startedAt(LocalDateTime.now())
                    .finishedAt(LocalDateTime.now())
                    .message("Skipped: a previous run was still in progress")
                    .build();
            return schedulerLogRepository.save(skipped);
        }

        SchedulerLog logEntry = SchedulerLog.builder()
                .status(SchedulerRunStatus.RUNNING)
                .startedAt(LocalDateTime.now())
                .build();
        logEntry = schedulerLogRepository.save(logEntry);

        try {
            JobIngestionService.IngestionResult result = ingestionService.ingest();
            logEntry.setStatus(SchedulerRunStatus.SUCCESS);
            logEntry.setFetchedCount(result.fetched());
            logEntry.setNewCount(result.stored());
            logEntry.setDuplicateCount(result.duplicates());
            logEntry.setNotifiedCount(result.notified());
            logEntry.setMessage("Completed successfully");
        } catch (Exception e) {
            log.error("Ingestion run failed: {}", e.getMessage(), e);
            logEntry.setStatus(SchedulerRunStatus.FAILED);
            logEntry.setMessage("Failed: " + e.getMessage());
        } finally {
            logEntry.setFinishedAt(LocalDateTime.now());
            schedulerLogRepository.save(logEntry);
            running.set(false);
        }
        return logEntry;
    }
}
