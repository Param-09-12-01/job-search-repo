package com.jobcopilot.service;

import com.jobcopilot.dto.common.PageResponse;
import com.jobcopilot.dto.scheduler.SchedulerLogResponse;
import com.jobcopilot.mapper.SchedulerLogMapper;
import com.jobcopilot.repository.SchedulerLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read access to scheduler run logs.
 */
@Service
@RequiredArgsConstructor
public class SchedulerLogService {

    private final SchedulerLogRepository schedulerLogRepository;
    private final SchedulerLogMapper schedulerLogMapper;

    @Transactional(readOnly = true)
    public PageResponse<SchedulerLogResponse> list(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
        return PageResponse.from(
                schedulerLogRepository.findAllByOrderByStartedAtDesc(pageable),
                schedulerLogMapper::toResponse);
    }
}
