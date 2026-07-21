package com.jobcopilot.service;

import com.jobcopilot.dto.common.CursorPageResponse;
import com.jobcopilot.dto.common.PageResponse;
import com.jobcopilot.dto.posting.DismissJobRequest;
import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.dto.posting.SaveJobRequest;
import com.jobcopilot.entity.DismissedJob;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.SavedJob;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.mapper.PostingMapper;
import com.jobcopilot.repository.ApplicationRepository;
import com.jobcopilot.repository.DismissedJobRepository;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.repository.SavedJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PostingService {

    private static final Set<String> SORTABLE = Set.of("score", "postedAt", "createdAt", "company", "title");

    private final PostingRepository postingRepository;
    private final SavedJobRepository savedJobRepository;
    private final DismissedJobRepository dismissedJobRepository;
    private final ApplicationRepository applicationRepository;
    private final PostingMapper postingMapper;

    @Transactional(readOnly = true)
    public PageResponse<PostingResponse> list(String query, Integer minScore, Boolean remote, String source,
                                              String company, boolean includeDismissed,
                                              int page, int size, String sortBy, String sortDir) {
        List<Long> dismissedIds = includeDismissed ? List.of() : dismissedJobRepository.findAllDismissedPostingIds();

        Specification<Posting> spec = PostingSpecifications.combine(List.of(
                PostingSpecifications.notArchived(),
                PostingSpecifications.search(query),
                PostingSpecifications.minScore(minScore),
                PostingSpecifications.remote(remote),
                PostingSpecifications.source(source),
                PostingSpecifications.company(company),
                PostingSpecifications.notIn(dismissedIds)
        ));

        Pageable pageable = buildPageable(page, size, sortBy, sortDir);
        Page<Posting> result = postingRepository.findAll(spec, pageable);
        return PageResponse.from(result, this::toResponse);
    }

    @Transactional(readOnly = true)
    public PostingResponse getById(Long id) {
        Posting posting = findPosting(id);
        return toResponse(posting);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<PostingResponse> getBySchedulerRunId(Long schedulerRunId, String cursor, int size) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        Pageable pageable = PageRequest.of(0, safeSize + 1);
        List<Posting> postings;
        if (cursor != null && !cursor.isBlank()) {
            String[] parts = cursor.split("_");
            int cursorScore = Integer.parseInt(parts[0]);
            Long cursorId = Long.parseLong(parts[1]);
            postings = postingRepository.findBySchedulerRunIdCursorAfter(schedulerRunId, cursorScore, cursorId, pageable);
        } else {
            postings = postingRepository.findBySchedulerRunIdCursor(schedulerRunId, pageable);
        }
        if (postings.isEmpty()) {
            return new CursorPageResponse<>(List.of(), null, false, 0);
        }
        List<Long> ids = postings.stream().map(Posting::getId).toList();
        Set<Long> savedIds = savedJobRepository.findSavedPostingIds(ids);
        Set<Long> dismissedIds = dismissedJobRepository.findDismissedPostingIds(ids);
        Set<Long> appliedIds = applicationRepository.findAppliedPostingIds(ids);
        return CursorPageResponse.from(postings, safeSize, p -> postingMapper.toResponse(p,
                savedIds.contains(p.getId()),
                dismissedIds.contains(p.getId()),
                appliedIds.contains(p.getId())));
    }

    @Transactional
    public PostingResponse save(Long postingId, SaveJobRequest request) {
        Posting posting = findPosting(postingId);
        SavedJob saved = savedJobRepository.findByPostingId(postingId)
                .orElseGet(() -> SavedJob.builder().posting(posting).build());
        saved.setNote(request == null ? null : request.note());
        savedJobRepository.save(saved);
        return toResponse(posting);
    }

    @Transactional
    public void unsave(Long postingId) {
        if (!savedJobRepository.existsByPostingId(postingId)) {
            throw new ResourceNotFoundException("Saved job not found for posting id: " + postingId);
        }
        savedJobRepository.deleteByPostingId(postingId);
    }

    @Transactional
    public PostingResponse dismiss(Long postingId, DismissJobRequest request) {
        Posting posting = findPosting(postingId);
        if (!dismissedJobRepository.existsByPostingId(postingId)) {
            dismissedJobRepository.save(DismissedJob.builder()
                    .posting(posting)
                    .reason(request == null ? null : request.reason())
                    .build());
        }
        return toResponse(posting);
    }

    private Pageable buildPageable(int page, int size, String sortBy, String sortDir) {
        String property = (sortBy != null && SORTABLE.contains(sortBy)) ? sortBy : "score";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        return PageRequest.of(safePage, safeSize, Sort.by(direction, property));
    }

    private Posting findPosting(Long id) {
        return postingRepository.findOne(
                        PostingSpecifications.notArchived()
                                .and((root, cq, cb) -> cb.equal(root.get("id"), id)))
                .orElseThrow(() -> ResourceNotFoundException.of("Posting", id));
    }

    private PostingResponse toResponse(Posting posting) {
        boolean saved = savedJobRepository.existsByPostingId(posting.getId());
        boolean dismissed = dismissedJobRepository.existsByPostingId(posting.getId());
        boolean applied = applicationRepository.existsByPostingId(posting.getId());
        return postingMapper.toResponse(posting, saved, dismissed, applied);
    }
}
