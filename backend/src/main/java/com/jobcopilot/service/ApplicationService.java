package com.jobcopilot.service;

import com.jobcopilot.dto.application.ApplicationResponse;
import com.jobcopilot.dto.application.CreateApplicationRequest;
import com.jobcopilot.dto.application.UpdateApplicationRequest;
import com.jobcopilot.entity.Application;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.enums.ApplicationMethod;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.exception.ConflictException;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.mapper.ApplicationMapper;
import com.jobcopilot.repository.ApplicationRepository;
import com.jobcopilot.repository.DismissedJobRepository;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.repository.SavedJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the application lifecycle and the Kanban board. Enforces the single-application-per-posting
 * invariant and stamps {@code appliedDate} when an application transitions to APPLIED.
 */
@Service
@RequiredArgsConstructor
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final PostingRepository postingRepository;
    private final SavedJobRepository savedJobRepository;
    private final DismissedJobRepository dismissedJobRepository;
    private final ApplicationMapper applicationMapper;

    @Transactional(readOnly = true)
    public List<ApplicationResponse> getAll() {
        return applicationRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Map<ApplicationStatus, List<ApplicationResponse>> getBoard() {
        Map<ApplicationStatus, List<ApplicationResponse>> board = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus status : ApplicationStatus.values()) {
            board.put(status, new java.util.ArrayList<>());
        }
        applicationRepository.findAll().forEach(app ->
                board.get(app.getStatus()).add(toResponse(app)));
        return board;
    }

    @Transactional(readOnly = true)
    public ApplicationResponse getById(Long id) {
        return toResponse(findApplication(id));
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        Posting posting = postingRepository.findById(request.postingId())
                .orElseThrow(() -> ResourceNotFoundException.of("Posting", request.postingId()));
        if (applicationRepository.existsByPostingId(posting.getId())) {
            throw new ConflictException("An application already exists for posting id: " + posting.getId());
        }
        Application application = Application.builder()
                .posting(posting)
                .status(ApplicationStatus.SAVED)
                .notes(request.notes())
                .method(request.method() == null ? ApplicationMethod.MANUAL : request.method())
                .build();
        return toResponse(applicationRepository.save(application));
    }

    @Transactional
    public ApplicationResponse update(Long id, UpdateApplicationRequest request) {
        Application application = findApplication(id);
        ApplicationStatus previous = application.getStatus();
        application.setStatus(request.status());
        if (request.notes() != null) {
            application.setNotes(request.notes());
        }
        if (request.method() != null) {
            application.setMethod(request.method());
        }
        // Stamp appliedDate the first time it enters APPLIED.
        if (request.status() == ApplicationStatus.APPLIED
                && previous != ApplicationStatus.APPLIED
                && application.getAppliedDate() == null) {
            application.setAppliedDate(LocalDateTime.now(ZoneOffset.UTC));
        }
        return toResponse(applicationRepository.save(application));
    }

    @Transactional
    public void delete(Long id) {
        Application application = findApplication(id);
        applicationRepository.delete(application);
    }

    private Application findApplication(Long id) {
        return applicationRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Application", id));
    }

    private ApplicationResponse toResponse(Application application) {
        Long postingId = application.getPosting().getId();
        boolean saved = savedJobRepository.existsByPostingId(postingId);
        boolean dismissed = dismissedJobRepository.existsByPostingId(postingId);
        return applicationMapper.toResponse(application, saved, dismissed);
    }
}
