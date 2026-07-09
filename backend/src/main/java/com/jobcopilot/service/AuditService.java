package com.jobcopilot.service;

import com.jobcopilot.entity.AuditLog;
import com.jobcopilot.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Persists security-relevant audit events. Writes are asynchronous so they never
 * block the request path.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    @Async
    public void record(String username, String action, String detail, String ipAddress) {
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .detail(detail)
                .ipAddress(ipAddress)
                .build();
        auditLogRepository.save(entry);
    }
}
