package com.jobcopilot.dto.profile;

import com.jobcopilot.entity.enums.RemotePreference;
import com.jobcopilot.entity.enums.Seniority;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.List;

/**
 * Request payload to create/update the user's profile.
 */
public record ProfileRequest(
        @NotBlank(message = "fullName is required") String fullName,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        String phone,
        String resumePath,
        String linkedIn,
        String github,
        String portfolio,
        List<String> titles,
        List<String> locations,
        @PositiveOrZero(message = "salaryMinimum must be >= 0") Integer salaryMinimum,
        RemotePreference remotePreference,
        Seniority seniority,
        List<String> keywords,
        List<String> excludedCompanies
) {
}
