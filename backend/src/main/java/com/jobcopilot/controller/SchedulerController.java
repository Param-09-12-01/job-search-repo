package com.jobcopilot.controller;

import com.jobcopilot.dto.common.CursorPageResponse;
import com.jobcopilot.dto.common.PageResponse;
import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.dto.scheduler.SchedulerLogResponse;
import com.jobcopilot.mapper.SchedulerLogMapper;
import com.jobcopilot.scheduler.JobFetchRunner;
import com.jobcopilot.service.PostingService;
import com.jobcopilot.service.SchedulerLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Scheduler endpoints: view run history and trigger ingestion manually.
 */
@RestController
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
@Tag(name = "Scheduler", description = "View scheduler runs and trigger ingestion manually")
public class SchedulerController {

    private final SchedulerLogService schedulerLogService;
    private final JobFetchRunner jobFetchRunner;
    private final SchedulerLogMapper schedulerLogMapper;
    private final PostingService postingService;

    @GetMapping("/logs")
    @Operation(summary = "List scheduler run logs (paginated)")
    public ResponseEntity<PageResponse<SchedulerLogResponse>> logs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(schedulerLogService.list(page, size));
    }

    @PostMapping("/run")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Trigger an ingestion run immediately")
    public ResponseEntity<SchedulerLogResponse> runNow() {
        return ResponseEntity.ok(schedulerLogMapper.toResponse(jobFetchRunner.runOnce()));
    }

    @GetMapping("/logs/{id}/jobs")
    @Operation(summary = "List postings fetched during a specific scheduler run (cursor-based)")
    public ResponseEntity<CursorPageResponse<PostingResponse>> jobsForRun(
            @PathVariable Long id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(postingService.getBySchedulerRunId(id, cursor, size));
    }
}
