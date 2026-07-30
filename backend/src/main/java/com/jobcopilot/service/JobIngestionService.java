package com.jobcopilot.service;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.entity.SourceFetchState;
import com.jobcopilot.integration.JobSourceAdapter;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.repository.SourceFetchStateRepository;
import com.jobcopilot.service.scoring.JobScoringService;
import com.jobcopilot.util.HashUtil;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Core ingestion pipeline: fetch → normalize → de-duplicate → score → store → notify.
 *
 * <p>All registered {@link JobSourceAdapter} beans are injected, so enabling a new provider only
 * requires adding an adapter. Per-source failures are isolated: one failing provider never aborts
 * the whole run.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobIngestionService {

    private final List<JobSourceAdapter> adapters;
    private final PostingRepository postingRepository;
    private final SourceFetchStateRepository sourceFetchStateRepository;
    private final ProfileService profileService;
    private final JobScoringService scoringService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final AppProperties properties;
    private final EntityManager entityManager;

    /**
     * Aggregated outcome of a single ingestion run.
     */
    public record IngestionResult(int fetched, int stored, int duplicates, int notified) {
    }

    @Transactional
    public IngestionResult ingest() {
        return ingest(null);
    }

    @Transactional
    public IngestionResult ingest(Long schedulerRunId) {
        Profile profile = profileService.getProfileEntityOrNull();
        int fetched = 0;
        int stored = 0;
        int duplicates = 0;
        int notified = 0;

        int threshold = settingsService.getIntValue("notification.score-threshold",
                properties.getNotification().getScoreThreshold());

        // De-dup within a single run as well as against the database.
        Set<String> seenFingerprints = new HashSet<>();

        for (JobSourceAdapter adapter : adapters) {
            if (!adapter.validate()) {
                log.debug("Skipping disabled/misconfigured adapter: {}", adapter.type());
                continue;
            }
            String sourceName = adapter.type().name();
            Instant lastPosted = sourceFetchStateRepository.findById(sourceName)
                    .map(SourceFetchState::getLastPostedAt)
                    .orElse(null);
            LocalDateTime since = lastPosted != null
                    ? LocalDateTime.ofInstant(lastPosted, ZoneOffset.UTC)
                    : null;
            if (since != null) {
                log.debug("Fetching {} with since={}", sourceName, since);
            }
            try {
                List<Object> raw = adapter.fetchJobs(since);
                List<NormalizedJob> normalized = adapter.normalize(raw);
                fetched += normalized.size();

                LocalDateTime maxPostedAt = null;
                for (NormalizedJob job : normalized) {
                    String fingerprint = HashUtil.fingerprint(job.title(), job.company(), job.location());
                    if (seenFingerprints.contains(fingerprint)
                            || postingRepository.existsByFingerprint(fingerprint)
                            || postingRepository.existsBySourceAndExternalId(job.source(), job.externalId())) {
                        duplicates++;
                        continue;
                    }
                    seenFingerprints.add(fingerprint);

                    int score = scoringService.score(job, profile);
                    Posting posting = toPosting(job, fingerprint, score, schedulerRunId);
                    postingRepository.save(posting);
                    stored++;

                    if (job.postedAt() != null && (maxPostedAt == null || job.postedAt().isAfter(maxPostedAt))) {
                        maxPostedAt = job.postedAt();
                    }

                    if (score >= threshold) {
                        notified += notifyHighScore(posting);
                    }
                }

                if (since == null && maxPostedAt != null) {
                    sourceFetchStateRepository.save(SourceFetchState.builder()
                            .source(sourceName)
                            .lastPostedAt(maxPostedAt.toInstant(ZoneOffset.UTC))
                            .build());
                    log.debug("Initialized {} lastPostedAt={}", sourceName, maxPostedAt);
                } else if (maxPostedAt != null) {
                    updateFetchState(sourceName, maxPostedAt.toInstant(ZoneOffset.UTC));
                }
            } catch (Exception e) {
                // Isolate provider failures — log, clear Hibernate session, continue with next adapter.
                log.error("Adapter {} failed during ingestion: {}", adapter.type(), e.getMessage(), e);
                entityManager.clear();
            }
        }

        log.info("Ingestion complete: fetched={}, stored={}, duplicates={}, notified={}",
                fetched, stored, duplicates, notified);
        archiveOldJobs();
        return new IngestionResult(fetched, stored, duplicates, notified);
    }

    private void updateFetchState(String source, Instant maxPostedAt) {
        sourceFetchStateRepository.findById(source).ifPresent(state -> {
            if (maxPostedAt.isAfter(state.getLastPostedAt())) {
                state.setLastPostedAt(maxPostedAt);
                state.setUpdatedAt(Instant.now());
                sourceFetchStateRepository.save(state);
                log.debug("Updated {} lastPostedAt={}", source, maxPostedAt);
            }
        });
    }

    /** Soft-delete jobs older than 15 days so they no longer appear in the UI. */
    private void archiveOldJobs() {
        LocalDateTime cutoff = java.time.LocalDateTime.now(ZoneOffset.UTC).minusDays(15);
        int archived = postingRepository.archiveOlderThan(cutoff);
        if (archived > 0) {
            log.info("Archived {} posting(s) older than {} days", archived, 15);
        }
    }

    private int notifyHighScore(Posting posting) {
        String title = "New job match (score %d): %s".formatted(posting.getScore(), posting.getTitle());
        String message = """
                A new posting matched your preferences.

                Title: %s
                Company: %s
                Location: %s
                Score: %d/100
                Link: %s
                """.formatted(
                posting.getTitle(),
                posting.getCompany() == null ? "N/A" : posting.getCompany(),
                posting.getLocation() == null ? "N/A" : posting.getLocation(),
                posting.getScore(),
                posting.getUrl());
        return notificationService.dispatchHighScore(title, message, posting) > 0 ? 1 : 0;
    }

    private Posting toPosting(NormalizedJob job, String fingerprint, int score, Long schedulerRunId) {
        return Posting.builder()
                .source(job.source())
                .externalId(job.externalId())
                .title(truncate(job.title(), 512))
                .company(truncate(job.company(), 255))
                .location(truncate(job.location(), 255))
                .remote(job.remote())
                .salary(truncate(job.salary(), 120))
                .salaryMin(job.salaryMin())
                .salaryMax(job.salaryMax())
                .description(job.description())
                .url(truncate(job.url(), 1024))
                .score(score)
                .fingerprint(fingerprint)
                .postedAt(job.postedAt())
                .schedulerRunId(schedulerRunId)
                .build();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
