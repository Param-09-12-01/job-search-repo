package com.jobcopilot.dto.posting;

import java.time.LocalDateTime;

/**
 * A normalized posting as returned to clients, including derived save/dismiss/application flags.
 */
public record PostingResponse(
        Long id,
        String source,
        String title,
        String company,
        String location,
        boolean remote,
        String salary,
        Integer salaryMin,
        Integer salaryMax,
        String description,
        String url,
        int score,
        LocalDateTime postedAt,
        LocalDateTime createdAt,
        boolean saved,
        boolean dismissed,
        boolean applied
) {
}
