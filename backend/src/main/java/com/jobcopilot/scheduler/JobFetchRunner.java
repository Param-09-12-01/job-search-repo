package com.jobcopilot.scheduler;

import com.jobcopilot.entity.SchedulerLog;
import com.jobcopilot.entity.enums.SchedulerRunStatus;
import com.jobcopilot.repository.SchedulerLogRepository;
import com.jobcopilot.service.JobIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs the ingestion pipeline and records a {@link SchedulerLog} for each run.
 * A guard prevents overlapping runs if a cycle takes longer than the interval.
 * The RUNNING status is flushed to the DB immediately so the frontend can
 * display live progress.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobFetchRunner {

    private final JobIngestionService ingestionService;
    private final SchedulerLogRepository schedulerLogRepository;
    private final TransactionTemplate transactionTemplate;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public SchedulerLog runOnce() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Ingestion run skipped — a previous run is still in progress");
            return transactionTemplate.execute(status -> {
                SchedulerLog skipped = SchedulerLog.builder()
                        .status(SchedulerRunStatus.SUCCESS)
                        .startedAt(Instant.now())
                        .finishedAt(Instant.now())
                        .message("Skipped: a previous run was still in progress")
                        .build();
                return schedulerLogRepository.save(skipped);
            });
        }

        // Save RUNNING status in its own transaction — visible to other sessions immediately.
        SchedulerLog logEntry = transactionTemplate.execute(status -> {
            SchedulerLog entry = SchedulerLog.builder()
                    .status(SchedulerRunStatus.RUNNING)
                    .startedAt(Instant.now())
                    .build();
            return schedulerLogRepository.save(entry);
        });
        if (logEntry == null) {
            running.set(false);
            throw new IllegalStateException("Failed to create scheduler log entry");
        }

        try {
            JobIngestionService.IngestionResult result = ingestionService.ingest(logEntry.getId());

            Long id = logEntry.getId();
            transactionTemplate.executeWithoutResult(status -> {
                SchedulerLog entry = schedulerLogRepository.findById(id).orElseThrow();
                entry.setStatus(SchedulerRunStatus.SUCCESS);
                entry.setFetchedCount(result.fetched());
                entry.setNewCount(result.stored());
                entry.setDuplicateCount(result.duplicates());
                entry.setNotifiedCount(result.notified());
                entry.setMessage("Completed successfully");
                entry.setFinishedAt(Instant.now());
                schedulerLogRepository.save(entry);
            });
            logEntry.setStatus(SchedulerRunStatus.SUCCESS);
        } catch (Exception e) {
            log.error("Ingestion run failed: {}", e.getMessage(), e);
            Long id = logEntry.getId();
            transactionTemplate.executeWithoutResult(status -> {
                SchedulerLog entry = schedulerLogRepository.findById(id).orElseThrow();
                entry.setStatus(SchedulerRunStatus.FAILED);
                entry.setFinishedAt(Instant.now());
                entry.setMessage("Failed: " + e.getMessage());
                schedulerLogRepository.save(entry);
            });
            logEntry.setStatus(SchedulerRunStatus.FAILED);
        } finally {
            running.set(false);
        }
        return logEntry;
    }
}
