package com.jobcopilot.service;

import com.jobcopilot.config.AppProperties;
import com.jobcopilot.dto.posting.NormalizedJob;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.Profile;
import com.jobcopilot.integration.JobSourceAdapter;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.service.scoring.JobScoringService;
import com.jobcopilot.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ProfileService profileService;
    private final JobScoringService scoringService;
    private final SettingsService settingsService;
    private final NotificationService notificationService;
    private final AppProperties properties;

    /**
     * Aggregated outcome of a single ingestion run.
     */
    public record IngestionResult(int fetched, int stored, int duplicates, int notified) {
    }

    @Transactional
    public IngestionResult ingest() {
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
            try {
                List<Object> raw = adapter.fetchJobs();
                List<NormalizedJob> normalized = adapter.normalize(raw);
                fetched += normalized.size();

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
                    Posting posting = toPosting(job, fingerprint, score);
                    postingRepository.save(posting);
                    stored++;

                    if (score >= threshold) {
                        notified += notifyHighScore(posting);
                    }
                }
            } catch (Exception e) {
                // Isolate provider failures — log and continue with the next adapter.
                log.error("Adapter {} failed during ingestion: {}", adapter.type(), e.getMessage(), e);
            }
        }

        log.info("Ingestion complete: fetched={}, stored={}, duplicates={}, notified={}",
                fetched, stored, duplicates, notified);
        return new IngestionResult(fetched, stored, duplicates, notified);
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

    private Posting toPosting(NormalizedJob job, String fingerprint, int score) {
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
                .build();
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
