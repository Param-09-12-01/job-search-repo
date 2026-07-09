package com.jobcopilot.mapper;

import com.jobcopilot.dto.application.ApplicationResponse;
import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.entity.Application;
import org.springframework.stereotype.Component;

/**
 * Builds {@link ApplicationResponse} objects, delegating the nested posting to {@link PostingMapper}.
 */
@Component
public class ApplicationMapper {

    private final PostingMapper postingMapper;

    public ApplicationMapper(PostingMapper postingMapper) {
        this.postingMapper = postingMapper;
    }

    public ApplicationResponse toResponse(Application a, boolean saved, boolean dismissed) {
        PostingResponse posting = a.getPosting() == null
                ? null
                : postingMapper.toResponse(a.getPosting(), saved, dismissed, true);
        return new ApplicationResponse(
                a.getId(),
                posting,
                a.getStatus(),
                a.getAppliedDate(),
                a.getNotes(),
                a.getMethod(),
                a.getCreatedAt(),
                a.getUpdatedAt()
        );
    }
}
