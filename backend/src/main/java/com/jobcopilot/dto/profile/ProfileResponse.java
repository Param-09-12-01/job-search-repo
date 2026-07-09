package com.jobcopilot.dto.profile;

import com.jobcopilot.entity.enums.RemotePreference;
import com.jobcopilot.entity.enums.Seniority;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Profile representation returned to clients.
 */
public record ProfileResponse(
        Long id,
        String fullName,
        String email,
        String phone,
        String resumePath,
        String linkedIn,
        String github,
        String portfolio,
        List<String> titles,
        List<String> locations,
        Integer salaryMinimum,
        RemotePreference remotePreference,
        Seniority seniority,
        List<String> keywords,
        List<String> excludedCompanies,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
