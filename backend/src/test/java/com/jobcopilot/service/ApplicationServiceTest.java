package com.jobcopilot.service;

import com.jobcopilot.dto.application.ApplicationResponse;
import com.jobcopilot.dto.application.CreateApplicationRequest;
import com.jobcopilot.dto.application.UpdateApplicationRequest;
import com.jobcopilot.entity.Application;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.exception.ConflictException;
import com.jobcopilot.exception.ResourceNotFoundException;
import com.jobcopilot.mapper.ApplicationMapper;
import com.jobcopilot.mapper.PostingMapper;
import com.jobcopilot.repository.ApplicationRepository;
import com.jobcopilot.repository.DismissedJobRepository;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.repository.SavedJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ApplicationService} using Mockito for all collaborators.
 */
@ExtendWith(MockitoExtension.class)
class ApplicationServiceTest {

    @Mock private ApplicationRepository applicationRepository;
    @Mock private PostingRepository postingRepository;
    @Mock private SavedJobRepository savedJobRepository;
    @Mock private DismissedJobRepository dismissedJobRepository;

    private ApplicationService applicationService;

    private Posting posting;

    @BeforeEach
    void setUp() {
        ApplicationMapper applicationMapper = new ApplicationMapper(new PostingMapper());
        applicationService = new ApplicationService(applicationRepository, postingRepository,
                savedJobRepository, dismissedJobRepository, applicationMapper);

        posting = Posting.builder().id(1L).title("Engineer").url("https://x").score(80)
                .fingerprint("fp").source("ADZUNA").externalId("e1").build();
    }

    @Test
    void createRejectsDuplicateApplication() {
        when(postingRepository.findById(1L)).thenReturn(Optional.of(posting));
        when(applicationRepository.existsByPostingId(1L)).thenReturn(true);

        assertThatThrownBy(() -> applicationService.create(new CreateApplicationRequest(1L, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createRejectsUnknownPosting() {
        when(postingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.create(new CreateApplicationRequest(99L, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void movingToAppliedStampsAppliedDate() {
        Application application = Application.builder()
                .id(5L).posting(posting).status(ApplicationStatus.SAVED).build();
        when(applicationRepository.findById(5L)).thenReturn(Optional.of(application));
        when(applicationRepository.save(any(Application.class))).thenAnswer(inv -> inv.getArgument(0));

        ApplicationResponse response = applicationService.update(5L,
                new UpdateApplicationRequest(ApplicationStatus.APPLIED, "sent", null));

        assertThat(response.status()).isEqualTo(ApplicationStatus.APPLIED);
        assertThat(application.getAppliedDate()).isNotNull();
    }

    @Test
    void updateUnknownApplicationThrows() {
        when(applicationRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> applicationService.update(123L,
                new UpdateApplicationRequest(ApplicationStatus.VIEWED, null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
