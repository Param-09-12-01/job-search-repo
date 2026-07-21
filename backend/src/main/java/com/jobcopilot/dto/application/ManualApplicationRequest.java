package com.jobcopilot.dto.application;

import com.jobcopilot.entity.enums.ApplicationMethod;
import com.jobcopilot.entity.enums.ApplicationStatus;
import jakarta.validation.constraints.NotBlank;

public record ManualApplicationRequest(
        @NotBlank(message = "title is required") String title,
        String company,
        String location,
        String url,
        String description,
        String salary,
        String notes,
        ApplicationStatus status,
        ApplicationMethod method
) {}
