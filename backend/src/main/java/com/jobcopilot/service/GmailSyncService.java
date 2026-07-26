package com.jobcopilot.service;

import com.jobcopilot.ai.OllamaClient;
import com.jobcopilot.dto.sync.GmailSyncResponse;
import com.jobcopilot.dto.sync.GmailSyncStatusResponse;
import com.jobcopilot.entity.Application;
import com.jobcopilot.entity.Posting;
import com.jobcopilot.entity.enums.ApplicationMethod;
import com.jobcopilot.entity.enums.ApplicationStatus;
import com.jobcopilot.gmail.GmailEmail;
import com.jobcopilot.gmail.GmailService;
import com.jobcopilot.repository.ApplicationRepository;
import com.jobcopilot.repository.PostingRepository;
import com.jobcopilot.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class GmailSyncService {

    private static final Logger log = LoggerFactory.getLogger(GmailSyncService.class);
    private static final double CONFIDENCE_THRESHOLD = 0.7;
    private static final int LOOKBACK_DAYS = 30;
    private static final int LAST_SYNC_OVERLAP_HOURS = 72;

    private final GmailService gmailService;
    private final OllamaClient ollamaClient;
    private final PostingRepository postingRepository;
    private final ApplicationRepository applicationRepository;
    private final SettingsService settingsService;

    private volatile boolean running = false;
    private volatile LocalDateTime lastSyncTime = null;
    private volatile GmailSyncResponse lastResult = null;

    public synchronized GmailSyncStatusResponse getStatus() {
        String refreshToken = settingsService.getValue("gmail.refresh-token", null);
        boolean authorized = refreshToken != null && !refreshToken.isBlank();
        String authUrl = null;
        if (!authorized) {
            authUrl = gmailService.generateAuthUrl();
        }
        return new GmailSyncStatusResponse(authorized, authUrl, lastSyncTime, lastResult, running);
    }

    public synchronized GmailSyncResponse sync() {
        return sync(null);
    }

    public synchronized GmailSyncResponse sync(Integer hours) {
        if (running) {
            log.warn("Gmail sync already in progress");
            return lastResult != null ? lastResult : new GmailSyncResponse(0, 0, 0, 0, 0, LocalDateTime.now(ZoneOffset.UTC));
        }

        running = true;
        try {
            LocalDateTime syncTime = LocalDateTime.now(ZoneOffset.UTC);
            LocalDateTime since;

            if (hours != null && hours > 0) {
                since = syncTime.minusHours(hours);
            } else if (lastSyncTime != null) {
                since = lastSyncTime.minusHours(LAST_SYNC_OVERLAP_HOURS);
            } else {
                since = syncTime.minusDays(LOOKBACK_DAYS);
            }

            log.info("Starting Gmail sync from {}", since);
            var emails = gmailService.fetchSentEmails(since);
            int scanned = emails.size();
            log.info("Fetched {} sent emails from Gmail", scanned);

            int detected = 0, added = 0, skipped = 0, errors = 0;

            for (GmailEmail email : emails) {
                var classification = ollamaClient.classify(email);

                if (!classification.isJobApplication() || classification.confidence() < CONFIDENCE_THRESHOLD) {
                    skipped++;
                    continue;
                }
                detected++;

                try {
                    String company = classification.company() != null ? classification.company() : extractCompanyFromEmail(email);
                    String jobTitle = classification.jobTitle() != null ? classification.jobTitle() : email.subject();
                    String location = "";

                    String fingerprint = HashUtil.fingerprint(jobTitle, company, location);
                    if (postingRepository.existsByFingerprint(fingerprint)) {
                        skipped++;
                        continue;
                    }

                    Posting posting = Posting.builder()
                            .source("GMAIL_SYNC")
                            .externalId("gmail-" + email.messageId())
                            .title(jobTitle)
                            .company(company)
                            .location(location)
                            .url("")
                            .description(email.body())
                            .salary("")
                            .fingerprint(fingerprint)
                            .build();
                    posting = postingRepository.save(posting);

                    Application application = Application.builder()
                            .posting(posting)
                            .status(ApplicationStatus.APPLIED)
                            .method(ApplicationMethod.PREPARED)
                            .appliedDate(email.sentDate())
                            .notes("Auto-detected from Gmail sent email")
                            .build();
                    applicationRepository.save(application);

                    added++;
                    log.info("Added application: {} @ {} (confidence: {})", jobTitle, company, classification.confidence());
                } catch (Exception e) {
                    errors++;
                    log.error("Failed to process email '{}': {}", email.subject(), e.getMessage());
                }
            }

            lastResult = new GmailSyncResponse(scanned, detected, added, skipped, errors, syncTime);
            lastSyncTime = syncTime;
            log.info("Gmail sync complete: scanned={}, detected={}, added={}, skipped={}, errors={}",
                    scanned, detected, added, skipped, errors);
            return lastResult;
        } finally {
            running = false;
        }
    }

    private String extractCompanyFromEmail(GmailEmail email) {
        for (String recipient : email.recipients()) {
            String domain = recipient.replaceAll(".*@", "").replaceAll("\\..*", "");
            if (!domain.isEmpty() && !domain.contains("gmail") && !domain.contains("yahoo") && !domain.contains("outlook")) {
                return domain.substring(0, 1).toUpperCase() + domain.substring(1);
            }
        }
        return email.recipients().isEmpty() ? "Unknown" : email.recipients().get(0);
    }
}
