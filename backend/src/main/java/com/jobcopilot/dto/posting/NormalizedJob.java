package com.jobcopilot.dto.posting;

/**
 * A normalized job posting produced by an adapter's {@code normalize()} step,
 * before it is persisted, scored, or de-duplicated. Immutable value object.
 */
public record NormalizedJob(
        String source,
        String externalId,
        String title,
        String company,
        String location,
        boolean remote,
        String salary,
        Integer salaryMin,
        Integer salaryMax,
        String description,
        String url,
        java.time.LocalDateTime postedAt
) {
}
