package com.jobcopilot.mapper;

import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.entity.Posting;
import org.springframework.stereotype.Component;

/**
 * Builds {@link PostingResponse} objects. Implemented as a plain component (not MapStruct)
 * because the derived {@code saved/dismissed/applied} flags are supplied by the service layer
 * from repository lookups rather than the entity itself.
 */
@Component
public class PostingMapper {

    public PostingResponse toResponse(Posting p, boolean saved, boolean dismissed, boolean applied) {
        return new PostingResponse(
                p.getId(),
                p.getSource(),
                p.getTitle(),
                p.getCompany(),
                p.getLocation(),
                p.isRemote(),
                p.getSalary(),
                p.getSalaryMin(),
                p.getSalaryMax(),
                p.getDescription(),
                p.getUrl(),
                p.getScore(),
                p.getPostedAt(),
                p.getCreatedAt(),
                saved,
                dismissed,
                applied
        );
    }
}
