package com.jobcopilot.service;

import com.jobcopilot.entity.Application;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Moves stale APPLIED applications to GHOSTED_BY_COMPANY when the company
 * hasn't responded within 7 days of the applied date.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GhostingService {

    private final ApplicationRepository applicationRepository;

    @Transactional
    public int ghostStaleApplied() {
        LocalDateTime cutoff = LocalDateTime.now(ZoneOffset.UTC).minusDays(7);
        var stale = applicationRepository.findStaleApplied(cutoff);
        if (stale.isEmpty()) {
            log.info("No stale APPLIED applications to ghost");
            return 0;
        }
        List<Long> ids = stale.stream().map(Application::getId).toList();
        int updated = applicationRepository.ghostByCompany(ids);
        log.info("Moved {} stale APPLIED application(s) to GHOSTED_BY_COMPANY", updated);
        return updated;
    }
}
